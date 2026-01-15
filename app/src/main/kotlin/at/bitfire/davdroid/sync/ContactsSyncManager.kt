/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.ContentProviderClient
import android.text.format.Formatter
import at.bitfire.dav4jvm.okhttp.DavAddressBook
import at.bitfire.dav4jvm.okhttp.MultiResponseCallback
import at.bitfire.dav4jvm.okhttp.Response
import at.bitfire.dav4jvm.okhttp.exception.DavException
import at.bitfire.dav4jvm.property.caldav.CalDAV
import at.bitfire.dav4jvm.property.carddav.AddressData
import at.bitfire.dav4jvm.property.carddav.CardDAV
import at.bitfire.dav4jvm.property.carddav.MaxResourceSize
import at.bitfire.dav4jvm.property.carddav.SupportedAddressData
import at.bitfire.dav4jvm.property.webdav.GetContentType
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.SupportedReportSet
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.di.SyncDispatcher
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
import at.bitfire.davdroid.util.DavUtils.sameTypeAs
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import ezvcard.VCardVersion
import ezvcard.io.CannotParseException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.Reader
import java.io.StringReader
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
 *   [LocalGroup.COLUMN_PENDING_MEMBERS]. In [postProcess],
 *   these "pending memberships" are assigned to the actual contacts and then cleaned up.
 *
 * @param syncFrameworkUpload   set when this sync is caused by the sync framework and [android.content.ContentResolver.SYNC_EXTRAS_UPLOAD] was set
 */
class ContactsSyncManager @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted httpClient: OkHttpClient,
    @Assisted syncResult: SyncResult,
    @Assisted val provider: ContentProviderClient,
    @Assisted localAddressBook: LocalAddressBook,
    @Assisted collection: Collection,
    @Assisted resync: ResyncType?,
    @Assisted val syncFrameworkUpload: Boolean,
    val dirtyVerifier: Optional<ContactDirtyVerifier>,
    accountSettingsFactory: AccountSettings.Factory,
    private val resourceRetrieverFactory: ResourceRetriever.Factory,
    @SyncDispatcher syncDispatcher: CoroutineDispatcher
): SyncManager<LocalAddress, LocalAddressBook, DavAddressBook>(
    account,
    httpClient,
    SyncDataType.CONTACTS,
    syncResult,
    localAddressBook,
    collection,
    resync,
    syncDispatcher
) {

    @AssistedFactory
    interface Factory {
        fun contactsSyncManager(
            account: Account,
            httpClient: OkHttpClient,
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
    private var hasJCard = false
    private val groupStrategy = when (accountSettings.getGroupMethod()) {
        GroupMethod.GROUP_VCARDS -> VCard4Strategy(localAddressBook)
        GroupMethod.CATEGORIES -> CategoriesStrategy(localAddressBook)
    }


    override fun prepare(): Boolean {
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
        return SyncException.wrapWithRemoteResourceSuspending(collection.url) {
            var syncState: SyncState? = null
            runInterruptible {
                davCollection.propfind(
                    0,
                    CardDAV.MaxResourceSize,
                    CardDAV.SupportedAddressData,
                    WebDAV.SupportedReportSet,
                    CalDAV.GetCTag,
                    WebDAV.SyncToken
                ) { response, relation ->
                    if (relation == Response.HrefRelation.SELF) {
                        response[MaxResourceSize::class.java]?.maxSize?.let { maxSize ->
                            logger.info("Address book accepts vCards up to ${Formatter.formatFileSize(context, maxSize)}")
                        }

                        response[SupportedAddressData::class.java]?.let { supported ->
                            hasVCard4 = supported.hasVCard4()

                            // temporarily disable jCard because of https://github.com/nextcloud/server/issues/29693
                            // hasJCard = supported.hasJCard()
                        }
                        response[SupportedReportSet::class.java]?.let { supported ->
                            hasCollectionSync = supported.reports.contains(WebDAV.SyncCollection)
                        }
                        syncState = syncState(response)
                    }
                }
            }

            // logger.info("Server supports jCard: $hasJCard")
            logger.info("Address book supports vCard4: $hasVCard4")
            logger.info("Address book supports Collection Sync: $hasCollectionSync")

            syncState
        }
    }

    override fun syncAlgorithm() =
        if (hasCollectionSync)
            SyncAlgorithm.COLLECTION_SYNC
        else
            SyncAlgorithm.PROPFIND_REPORT

    override suspend fun processLocallyDeleted() =
            if (localCollection.readOnly) {
                var modified = false
                for (group in localCollection.findDeletedGroups()) {
                    logger.warning("Restoring locally deleted group (read-only address book!)")
                    SyncException.wrapWithLocalResource(group) {
                        group.resetDeleted()
                    }
                    modified = true
                }

                for (contact in localCollection.findDeletedContacts()) {
                    logger.warning("Restoring locally deleted contact (read-only address book!)")
                    SyncException.wrapWithLocalResource(contact) {
                        contact.resetDeleted()
                    }
                    modified = true
                }

                /* This is unfortunately dirty: When a contact has been inserted to a read-only address book
                   that supports Collection Sync, it's not enough to force synchronization (by returning true),
                   but we also need to make sure all contacts are downloaded again. */
                if (modified)
                    localCollection.lastSyncState = null

                modified
            } else
                // mirror deletions to remote collection (DELETE)
                super.processLocallyDeleted()

    override suspend fun uploadDirty(): Boolean {
        var modified = false

        if (localCollection.readOnly) {
            for (group in localCollection.findDirtyGroups()) {
                logger.warning("Resetting locally modified group to ETag=null (read-only address book!)")
                SyncException.wrapWithLocalResource(group) {
                    group.clearDirty(Optional.empty(), null)
                }
                modified = true
            }

            for (contact in localCollection.findDirtyContacts()) {
                logger.warning("Resetting locally modified contact to ETag=null (read-only address book!)")
                SyncException.wrapWithLocalResource(contact) {
                    contact.clearDirty(Optional.empty(), null)
                }
                modified = true
            }

            // see same position in processLocallyDeleted
            if (modified)
                localCollection.lastSyncState = null

        } else
            // we only need to handle changes in groups when the address book is read/write
            groupStrategy.beforeUploadDirty()

        // generate UID/file name for newly created contacts
        val superModified = super.uploadDirty()

        // return true when any operation returned true
        return modified or superModified
    }

    override fun generateUpload(resource: LocalAddress): GeneratedResource {
        val contact: Contact = when (resource) {
            is LocalContact -> resource.getContact()
            is LocalGroup -> resource.getContact()
            else -> throw IllegalArgumentException("resource must be LocalContact or LocalGroup")
        }
        logger.log(Level.FINE, "Preparing upload of vCard #${resource.id}", contact)

        // get/create UID
        val (uid, uidIsGenerated) = DavUtils.generateUidIfNecessary(contact.uid)
        if (uidIsGenerated) {
            // modify in Contact and persist to contacts provider
            contact.uid = uid
            resource.updateUid(uid)
        }

        // generate vCard and convert to request body
        val os = ByteArrayOutputStream()
        val mimeType: MediaType
        when {
            hasJCard -> {
                mimeType = DavAddressBook.MIME_JCARD
                contact.writeJCard(os, Constants.vCardProdId)
            }
            hasVCard4 -> {
                mimeType = DavAddressBook.MIME_VCARD4
                contact.writeVCard(VCardVersion.V4_0, os, Constants.vCardProdId)
            }
            else -> {
                mimeType = DavAddressBook.MIME_VCARD3_UTF8
                contact.writeVCard(VCardVersion.V3_0, os, Constants.vCardProdId)
            }
        }

        return GeneratedResource(
            suggestedFileName = DavUtils.fileNameFromUid(uid, "vcf"),
            requestBody = os.toByteArray().toRequestBody(mimeType)
        )
    }

    override suspend fun listAllRemote(callback: MultiResponseCallback) =
        SyncException.wrapWithRemoteResourceSuspending(collection.url) {
            runInterruptible {
                davCollection.propfind(1, WebDAV.ResourceType, WebDAV.GetETag, callback = callback)
            }
        }

    override suspend fun downloadRemote(bunch: List<HttpUrl>) {
        logger.info("Downloading ${bunch.size} vCard(s): $bunch")
        SyncException.wrapWithRemoteResourceSuspending(collection.url) {
            val contentType: String?
            val version: String?
            when {
                hasJCard -> {
                    contentType = DavUtils.MEDIA_TYPE_JCARD.toString()
                    version = VCardVersion.V4_0.version
                }
                hasVCard4 -> {
                    contentType = DavUtils.MEDIA_TYPE_VCARD.toString()
                    version = VCardVersion.V4_0.version
                }
                else -> {
                    contentType = DavUtils.MEDIA_TYPE_VCARD.toString()
                    version = null     // 3.0 is the default version; don't request 3.0 explicitly because maybe some vCard3-only servers don't understand it
                }
            }
            runInterruptible {
                davCollection.multiget(bunch, contentType, version) { response, _ ->
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

                        var isJCard = hasJCard      // assume that server has sent what we have requested (we ask for jCard only when the server advertises it)
                        response[GetContentType::class.java]?.type?.toMediaTypeOrNull()?.let { type ->
                            isJCard = type.sameTypeAs(DavUtils.MEDIA_TYPE_JCARD)
                        }

                        processCard(
                            fileName = response.href.lastSegment,
                            eTag = eTag,
                            reader = StringReader(card),
                            jCard = isJCard,
                            downloader = object : Contact.Downloader {
                                override fun download(url: String, accepts: String): ByteArray? {
                                    // download external resource (like a photo) from an URL
                                    val downloader = resourceRetrieverFactory.create(account, davCollection.location.host)
                                    return runBlocking(syncDispatcher) {
                                        downloader.retrieve(url)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun postProcess() {
        groupStrategy.postProcess()
    }


    // helpers

    private fun processCard(fileName: String, eTag: String, reader: Reader, jCard: Boolean, downloader: Contact.Downloader) {
        logger.info("Processing CardDAV resource $fileName")

        val contacts = try {
            Contact.fromReader(reader, jCard, downloader)
        } catch (e: CannotParseException) {
            logger.log(Level.SEVERE, "Received invalid vCard, ignoring", e)
            notifyInvalidResource(e, fileName)
            return
        }

        if (contacts.isEmpty()) {
            logger.warning("Received vCard without data, ignoring")
            return
        } else if (contacts.size > 1)
            logger.warning("Received multiple vCards, using first one")

        val newData = contacts.first()
        groupStrategy.verifyContactBeforeSaving(newData)

        var updated: LocalAddress? = null

        val existing = localCollection.findByName(fileName)
        if (existing == null) {
            // create new contact/group
            if (newData.group) {
                logger.log(Level.INFO, "Creating local group", newData)
                val newGroup = LocalGroup(localCollection, newData, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT)
                SyncException.wrapWithLocalResource(newGroup) {
                    newGroup.add()
                    updated = newGroup
                }

            } else {
                logger.log(Level.INFO, "Creating local contact", newData)
                val newContact = LocalContact(localCollection, newData, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT)
                SyncException.wrapWithLocalResource(newContact) {
                    newContact.add()
                    updated = newContact
                }
            }

        } else {
            // update existing local contact/group
            logger.log(Level.INFO, "Updating $fileName in local address book", newData)

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
                        logger.log(Level.INFO, "Creating local group (was contact before)", newData)
                        val newGroup = LocalGroup(localCollection, newData, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT)
                        SyncException.wrapWithLocalResource(newGroup) {
                            newGroup.add()
                            updated = newGroup
                        }

                    } else {
                        logger.log(Level.INFO, "Creating local contact (was group before)", newData)
                        val newContact = LocalContact(localCollection, newData, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT)
                        SyncException.wrapWithLocalResource(newContact) {
                            newContact.add()
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