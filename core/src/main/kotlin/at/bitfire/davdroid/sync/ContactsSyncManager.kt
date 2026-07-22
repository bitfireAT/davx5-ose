/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.ContentProviderClient
import android.text.format.Formatter
import at.bitfire.dav4jvm.ktor.DavAddressBook
import at.bitfire.dav4jvm.ktor.MultiStatusItem
import at.bitfire.dav4jvm.ktor.exception.DavException
import at.bitfire.dav4jvm.ktor.responses
import at.bitfire.dav4jvm.ktor.selfResponse
import at.bitfire.dav4jvm.property.caldav.CalDAV
import at.bitfire.dav4jvm.property.carddav.AddressData
import at.bitfire.dav4jvm.property.carddav.CardDAV
import at.bitfire.dav4jvm.property.carddav.MaxResourceSize
import at.bitfire.dav4jvm.property.carddav.SupportedAddressData
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.SupportedReportSet
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.ProductIds
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.di.qualifier.SyncTransferSemaphore
import at.bitfire.davdroid.resource.LocalAddress
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.resource.LocalContact
import at.bitfire.davdroid.resource.LocalGroup
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.resource.SyncState
import at.bitfire.davdroid.resource.workaround.ContactDirtyVerifier
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.groups.CategoriesStrategy
import at.bitfire.davdroid.sync.groups.VCard4Strategy
import at.bitfire.davdroid.util.DavUtils
import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.mapping.contacts.ContactReader
import at.bitfire.synctools.mapping.contacts.ContactWriter
import at.bitfire.synctools.vcard.GroupMethod
import at.bitfire.synctools.vcard.VCardParser
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import ezvcard.VCardVersion
import ezvcard.io.CannotParseException
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import java.util.Optional
import java.util.logging.Level
import kotlin.jvm.optionals.getOrNull

/**
 * Synchronization manager for CardDAV collections; handles contacts and groups.
 *
 * Group handling differs according to the {@link #groupMethod}. There are two basic methods to
 * handle/manage groups:
 *
 * 1. CATEGORIES: groups memberships are attached to each contact and represented as
 *   "category". When a group is dirty or has been deleted, all its members have to be set to
 *   dirty, too (because they have to be uploaded without the respective category). This
 *   is done in [uploadDirty]. Empty groups can be deleted without further processing,
 *   which is done in [postProcess] because groups may become empty after downloading
 *   updated remote contacts.
 *
 * 2. Groups as separate VCards: individual and group contacts (with a list of member UIDs) are
 *   distinguished. When a local group is dirty, its members don't need to be set to dirty.
 *
 *   However, when a contact is dirty, it has
 *   to be checked whether its group memberships have changed. In this case, the respective
 *   groups have to be set to dirty. For instance, if contact A is in group G and H, and then
 *   group membership of G is removed, the contact will be set to dirty because of the changed
 *   [android.provider.ContactsContract.CommonDataKinds.GroupMembership]. DAVx5 will
 *   then have to check whether the group memberships have actually changed, and if so,
 *   all affected groups have to be set to dirty. To detect changes in group memberships,
 *   DAVx5 always mirrors all [android.provider.ContactsContract.CommonDataKinds.GroupMembership]
 *   data rows in respective [at.bitfire.vcard4android.CachedGroupMembership] rows.
 *   If the cached group memberships are not the same as the current group member ships, the
 *   difference set (in our example G, because its in the cached memberships, but not in the
 *   actual ones) is marked as dirty. This is done in [uploadDirty].
 *
 *   When downloading remote contacts, groups (+ member information) may be received
 *   by the actual members. Thus, the member lists have to be cached until all VCards
 *   are received. This is done by caching the member UIDs of each group in
 *   [AddressContract.GroupColumns.PENDING_MEMBERS]. In [postProcess],
 *   these "pending memberships" are assigned to the actual contacts and then cleaned up.
 *
 * @param syncFrameworkUpload   set when this sync is caused by the sync framework and [android.content.ContentResolver.SYNC_EXTRAS_UPLOAD] was set
 */
class ContactsSyncManager @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted httpClient: HttpClient,
    @Assisted syncResult: SyncResult,
    @Assisted val provider: ContentProviderClient,
    @Assisted localAddressBook: LocalAddressBook,
    @Assisted collection: Collection,
    @Assisted resync: ResyncType?,
    @Assisted val syncFrameworkUpload: Boolean,
    accountSettingsFactory: AccountSettings.Factory,
    val dirtyVerifier: Optional<ContactDirtyVerifier>,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
    private val productIds: ProductIds,
    private val resourceRetrieverFactory: ResourceRetriever.Factory,
    @SyncTransferSemaphore syncTransferSemaphore: Semaphore
): SyncManager<LocalAddress, LocalAddressBook, DavAddressBook>(
    account,
    httpClient,
    SyncDataType.CONTACTS,
    syncResult,
    localAddressBook,
    collection,
    resync,
    ioDispatcher,
    syncTransferSemaphore
) {

    @AssistedFactory
    interface Factory {
        fun contactsSyncManager(
            account: Account,
            httpClient: HttpClient,
            syncResult: SyncResult,
            provider: ContentProviderClient,
            localAddressBook: LocalAddressBook,
            collection: Collection,
            resync: ResyncType?,
            syncFrameworkUpload: Boolean
        ): ContactsSyncManager
    }

    companion object {
        infix fun <T> Set<T>.disjunct(other: Set<T>) = (this - other) union (other - this)
    }

    private val accountSettings = accountSettingsFactory.create(account)

    private var hasVCard4 = false
    private val groupStrategy = when (accountSettings.getGroupMethod()) {
        GroupMethod.GROUP_VCARDS -> VCard4Strategy(localAddressBook)
        GroupMethod.CATEGORIES -> CategoriesStrategy(localAddressBook)
    }


    override suspend fun prepare(): Boolean {
        if (dirtyVerifier.isPresent) {
            logger.info("Sync will verify dirty contacts (Android 7.x workaround)")
            if (!dirtyVerifier.get().prepareAddressBook(localCollection, isUpload = syncFrameworkUpload))
                return false
        }

        davCollection = DavAddressBook(httpClient, collection.url)

        logger.info("Contact group strategy: ${groupStrategy::class.java.simpleName}")
        return true
    }

    override suspend fun queryCapabilities(): SyncState? {
        return SyncException.wrapWithRemoteResource(collection.url) {
            val response = davCollection.propfind(
                0,
                CardDAV.MaxResourceSize,
                CardDAV.SupportedAddressData,
                WebDAV.SupportedReportSet,
                CalDAV.GetCTag,
                WebDAV.SyncToken
            ).selfResponse() ?: return@wrapWithRemoteResource null

            response[MaxResourceSize::class.java]?.maxSize?.let { maxSize ->
                logger.info("Address book accepts vCards up to ${Formatter.formatFileSize(context, maxSize)}")
            }

            response[SupportedAddressData::class.java]?.let { supported ->
                hasVCard4 = supported.hasVCard4()
            }
            response[SupportedReportSet::class.java]?.let { supported ->
                hasCollectionSync = supported.reports.contains(WebDAV.SyncCollection)
            }

            logger.info("Address book supports vCard4: $hasVCard4")
            logger.info("Address book supports Collection Sync: $hasCollectionSync")

            syncState(response)
        }
    }

    override fun syncAlgorithm() =
        if (hasCollectionSync)
            collectionSyncAlgorithm()
        else
            propfindReportAlgorithm()

    override suspend fun uploadDirty(): Boolean {
        // local group housekeeping is needed regardless of whether we're actually uploading
        groupStrategy.resolveLocalGroupChanges()

        if (!localCollection.readOnly) {
            // preparing groups for upload is only relevant when local changes are pushed
            groupStrategy.beforeUploadDirty()
        }

        return super.uploadDirty()
    }

    override fun generateUpload(resource: LocalAddress): GeneratedResource {
        val contact: Contact = when (resource) {
            is LocalContact -> resource.androidContact.getContact()
            is LocalGroup -> resource.androidGroup.getContact()
            else -> throw IllegalArgumentException("resource must be LocalContact or LocalGroup")
        }
        logger.log(Level.FINE, "Preparing upload of vCard #{0}: {1}", arrayOf(resource.id, contact))

        // get/create UID
        val (uid, uidIsGenerated) = DavUtils.generateUidIfNecessary(contact.uid)
        if (uidIsGenerated) {
            // modify in Contact and persist to contacts provider
            contact.uid = uid
            resource.updateUid(uid)
        }

        // generate vCard and convert to request body
        val writer = StringWriter()
        val mimeType: ContentType
        val vCardVersion: VCardVersion
        when {
            hasVCard4 -> {
                mimeType = DavAddressBook.MIME_VCARD4
                vCardVersion = VCardVersion.V4_0
            }
            else -> {
                mimeType = DavAddressBook.MIME_VCARD3_UTF8
                vCardVersion = VCardVersion.V3_0
            }
        }
        ContactWriter(contact, vCardVersion, productIds.vCardProdId).writeVCard(writer)

        return GeneratedResource(
            suggestedFileName = DavUtils.fileNameFromUid(uid, "vcf"),
            content = TextContent(text = writer.toString(), contentType = mimeType)
        )
    }

    override fun listAllRemote(): Flow<MultiStatusItem> = flow {
        SyncException.wrapWithRemoteResource(collection.url) {
            emitAll(davCollection.propfind(1, WebDAV.ResourceType, WebDAV.GetETag))
        }
    }

    override suspend fun downloadRemote(bunch: List<Url>) {
        logger.info("Downloading ${bunch.size} vCard(s): $bunch")
        SyncException.wrapWithRemoteResource(collection.url) {
            val contentType: String?
            val version: String?
            when {
                hasVCard4 -> {
                    contentType = DavUtils.MEDIA_TYPE_VCARD.toString()
                    version = VCardVersion.V4_0.version
                }
                else -> {
                    contentType = DavUtils.MEDIA_TYPE_VCARD.toString()
                    version = null     // 3.0 is the default version; don't request 3.0 explicitly because maybe some vCard3-only servers don't understand it
                }
            }
            davCollection.multiget(bunch, contentType, version).responses().collect { response ->
                // See CalendarSyncManager for more information about the multi-get response
                SyncException.wrapWithRemoteResource(response.href) wrapResource@{
                    if (!response.isSuccess()) {
                        logger.warning("Ignoring non-successful multi-get response for ${response.href}")
                        return@wrapResource
                    }

                    val card = response[AddressData::class.java]?.card
                    if (card == null) {
                        logger.warning("Ignoring multi-get response without address-data")
                        return@wrapResource
                    }

                    val eTag = response[GetETag::class.java]?.eTag
                        ?: throw DavException("Received multi-get response without ETag")

                    processCard(
                        fileName = response.href.lastSegment,
                        eTag = eTag,
                        reader = StringReader(card),
                        downloader = object : Contact.Downloader {
                            override suspend fun download(url: String, accepts: String): ByteArray? {
                                // retrieve external resource (like a photo) from a URL (not necessarily HTTP[S])
                                val retriever = resourceRetrieverFactory.create(account, davCollection.location.host)
                                return retriever.retrieve(url)
                            }
                        }
                    )
                }
            }
        }
    }

    override suspend fun postProcess() {
        groupStrategy.postProcess()
    }


    // helpers

    private suspend fun processCard(fileName: String, eTag: String, reader: Reader, downloader: Contact.Downloader) {
        logger.info("Processing CardDAV resource $fileName")

        val newData = try {
            // parse vCard
            val vCard = VCardParser().parse(reader).firstOrNull()
            if (vCard == null) {
                logger.warning("Received vCard without data, ignoring")
                return
            }

            // map to Contact
            ContactReader.fromVCard(vCard, downloader)
        } catch (e: CannotParseException) {
            logger.log(Level.SEVERE, "Received invalid vCard, ignoring", e)
            notifyInvalidResource(e, fileName)
            return
        }

        groupStrategy.verifyContactBeforeSaving(newData)

        var updated: LocalAddress? = null

        val existing = localCollection.findByName(fileName)
        if (existing == null) {
            // create new contact/group
            if (newData.group) {
                logger.info("Creating local group: $newData")
                val newGroup = localCollection.addGroup(newData, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT)
                SyncException.wrapWithLocalResource(newGroup) {
                    updated = newGroup
                }

            } else {
                logger.info("Creating local contact: $newData")
                val newContact = localCollection.addContact(newData, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT)
                SyncException.wrapWithLocalResource(newContact) {
                    updated = newContact
                }
            }

        } else {
            // update existing local contact/group
            logger.info("Updating $fileName in local address book: $newData")

            SyncException.wrapWithLocalResource(existing) {
                if ((existing is LocalGroup && newData.group) || (existing is LocalContact && !newData.group)) {
                    // update contact / group

                    existing.update(
                        data = newData,
                        fileName = fileName,
                        eTag = eTag,
                        flags = LocalResource.FLAG_REMOTELY_PRESENT,
                        scheduleTag = null
                    )
                    updated = existing

                } else {
                    // group has become an individual contact or vice versa, delete and create with new type
                    existing.deleteLocal()

                    if (newData.group) {
                        logger.info("Creating local group (was contact before): $newData")
                        val newGroup = localCollection.addGroup(newData, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT)
                        SyncException.wrapWithLocalResource(newGroup) {
                            updated = newGroup
                        }

                    } else {
                        logger.info("Creating local contact (was group before): $newData")
                        val newContact = localCollection.addContact(newData, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT)
                        SyncException.wrapWithLocalResource(newContact) {
                            updated = newContact
                        }
                    }
                }
            }
        }

        // update hash code of updated contact, if applicable
        (updated as? LocalContact)?.let { updatedContact ->
            // workaround for Android 7 which sets DIRTY flag when only meta-data is changed
            dirtyVerifier.getOrNull()?.updateHashCode(localCollection, updatedContact)
        }
    }


    override fun notifyInvalidResourceTitle(): String =
            context.getString(R.string.sync_invalid_contact)

}