/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.SyncResult
import android.os.Build
import android.text.format.Formatter
import at.bitfire.dav4jvm.DavAddressBook
import at.bitfire.dav4jvm.MultiResponseCallback
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.property.caldav.GetCTag
import at.bitfire.dav4jvm.property.carddav.AddressData
import at.bitfire.dav4jvm.property.carddav.MaxResourceSize
import at.bitfire.dav4jvm.property.carddav.SupportedAddressData
import at.bitfire.dav4jvm.property.webdav.GetContentType
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.ResourceType
import at.bitfire.dav4jvm.property.webdav.SupportedReportSet
import at.bitfire.dav4jvm.property.webdav.SyncToken
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.SyncState
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.resource.LocalAddress
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.resource.LocalContact
import at.bitfire.davdroid.resource.LocalGroup
import at.bitfire.davdroid.resource.LocalResource
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
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.Reader
import java.io.StringReader
import java.util.logging.Level
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

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
 */
class ContactsSyncManager @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted accountSettings: AccountSettings,
    @Assisted httpClient: HttpClient,
    @Assisted extras: Array<String>,
    @Assisted authority: String,
    @Assisted syncResult: SyncResult,
    @Assisted val provider: ContentProviderClient,
    @Assisted localAddressBook: LocalAddressBook,
    @Assisted collection: Collection
): SyncManager<LocalAddress, LocalAddressBook, DavAddressBook>(
    account,
    accountSettings,
    httpClient,
    extras,
    authority,
    syncResult,
    localAddressBook,
    collection
) {

    @AssistedFactory
    interface Factory {
        fun contactsSyncManager(
            account: Account,
            accountSettings: AccountSettings,
            httpClient: HttpClient,
            extras: Array<String>,
            authority: String,
            syncResult: SyncResult,
            provider: ContentProviderClient,
            localAddressBook: LocalAddressBook,
            collection: Collection
        ): ContactsSyncManager
    }

    companion object {
        infix fun <T> Set<T>.disjunct(other: Set<T>) = (this - other) union (other - this)
    }

    private var hasVCard4 = false
    private var hasJCard = false
    private val groupStrategy = when (accountSettings.getGroupMethod()) {
        GroupMethod.GROUP_VCARDS -> VCard4Strategy(localAddressBook)
        GroupMethod.CATEGORIES -> CategoriesStrategy(localAddressBook)
    }

    /**
     * Used to download images which are referenced by URL
     */
    private lateinit var resourceDownloader: ResourceDownloader


    override fun prepare() {
        davCollection = DavAddressBook(httpClient.okHttpClient, collection.url)

        resourceDownloader = ResourceDownloader(davCollection.location)

        logger.info("Contact group strategy: ${groupStrategy::class.java.simpleName}")
    }

    override fun queryCapabilities(): SyncState? {
        return SyncException.wrapWithRemoteResource(collection.url) {
            var syncState: SyncState? = null
            davCollection.propfind(0, MaxResourceSize.NAME, SupportedAddressData.NAME, SupportedReportSet.NAME, GetCTag.NAME, SyncToken.NAME) { response, relation ->
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
                        hasCollectionSync = supported.reports.contains(SupportedReportSet.SYNC_COLLECTION)
                    }
                    syncState = syncState(response)
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

    override fun processLocallyDeleted() =
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

    override fun uploadDirty(): Boolean {
        var modified = false

        if (localCollection.readOnly) {
            for (group in localCollection.findDirtyGroups()) {
                logger.warning("Resetting locally modified group to ETag=null (read-only address book!)")
                SyncException.wrapWithLocalResource(group) {
                    group.clearDirty(null, null)
                }
                modified = true
            }

            for (contact in localCollection.findDirtyContacts()) {
                logger.warning("Resetting locally modified contact to ETag=null (read-only address book!)")
                SyncException.wrapWithLocalResource(contact) {
                    contact.clearDirty(null, null)
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

    override fun generateUpload(resource: LocalAddress): RequestBody =
        SyncException.wrapWithLocalResource(resource) {
            val contact: Contact = when (resource) {
                is LocalContact -> resource.getContact()
                is LocalGroup -> resource.getContact()
                else -> throw IllegalArgumentException("resource must be LocalContact or LocalGroup")
            }

            logger.log(Level.FINE, "Preparing upload of vCard ${resource.fileName}", contact)

            val os = ByteArrayOutputStream()
            val mimeType: MediaType
            when {
                hasJCard -> {
                    mimeType = DavAddressBook.MIME_JCARD
                    contact.writeJCard(os)
                }
                hasVCard4 -> {
                    mimeType = DavAddressBook.MIME_VCARD4
                    contact.writeVCard(VCardVersion.V4_0, os)
                }
                else -> {
                    mimeType = DavAddressBook.MIME_VCARD3_UTF8
                    contact.writeVCard(VCardVersion.V3_0, os)
                }
            }

            return@wrapWithLocalResource os.toByteArray().toRequestBody(mimeType)
        }

    override fun listAllRemote(callback: MultiResponseCallback) =
        SyncException.wrapWithRemoteResource(collection.url) {
            davCollection.propfind(1, ResourceType.NAME, GetETag.NAME, callback = callback)
        }

    override fun downloadRemote(bunch: List<HttpUrl>) {
        logger.info("Downloading ${bunch.size} vCard(s): $bunch")
        SyncException.wrapWithRemoteResource(collection.url) {
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
            davCollection.multiget(bunch, contentType, version) { response, _ ->
                SyncException.wrapWithRemoteResource(response.href) wrapResource@ {
                    if (!response.isSuccess()) {
                        logger.warning("Received non-successful multiget response for ${response.href}")
                        return@wrapResource
                    }

                    val eTag = response[GetETag::class.java]?.eTag
                            ?: throw DavException("Received multi-get response without ETag")

                    var isJCard = hasJCard      // assume that server has sent what we have requested (we ask for jCard only when the server advertises it)
                    response[GetContentType::class.java]?.type?.let { type ->
                        isJCard = type.sameTypeAs(DavUtils.MEDIA_TYPE_JCARD)
                    }

                    val addressData = response[AddressData::class.java]
                    val card = addressData?.card
                            ?: throw DavException("Received multi-get response without address data")

                    processCard(
                        response.href.lastSegment,
                        eTag,
                        StringReader(card),
                        isJCard,
                        resourceDownloader
                    )
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

        // update local contact, if it exists
        val localOrNull = localCollection.findByName(fileName)
        SyncException.wrapWithLocalResource(localOrNull) {
            var local = localOrNull
            if (local != null) {
                logger.log(Level.INFO, "Updating $fileName in local address book", newData)

                if (local is LocalGroup && newData.group) {
                    // update group
                    local.eTag = eTag
                    local.flags = LocalResource.FLAG_REMOTELY_PRESENT
                    local.update(newData)
                    syncResult.stats.numUpdates++

                } else if (local is LocalContact && !newData.group) {
                    // update contact
                    local.eTag = eTag
                    local.flags = LocalResource.FLAG_REMOTELY_PRESENT
                    local.update(newData)
                    syncResult.stats.numUpdates++

                } else {
                    // group has become an individual contact or vice versa, delete and create with new type
                    local.delete()
                    local = null
                }
            }

            if (local == null) {
                if (newData.group) {
                    logger.log(Level.INFO, "Creating local group", newData)
                    val newGroup = LocalGroup(localCollection, newData, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT)
                    SyncException.wrapWithLocalResource(newGroup) {
                        newGroup.add()
                        local = newGroup
                    }
                } else {
                    logger.log(Level.INFO, "Creating local contact", newData)
                    val newContact = LocalContact(localCollection, newData, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT)
                    SyncException.wrapWithLocalResource(newContact) {
                        newContact.add()
                        local = newContact
                    }
                }
                syncResult.stats.numInserts++
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
                // workaround for Android 7 which sets DIRTY flag when only meta-data is changed
                (local as? LocalContact)?.updateHashCode(null)
        }
    }


    // downloader helper class

    private inner class ResourceDownloader(
            val baseUrl: HttpUrl
    ): Contact.Downloader {

        override fun download(url: String, accepts: String): ByteArray? {
            val httpUrl = url.toHttpUrlOrNull()
            if (httpUrl == null) {
                logger.log(Level.SEVERE, "Invalid external resource URL", url)
                return null
            }

            // authenticate only against a certain host, and only upon request
            val client = HttpClient.Builder(context, baseUrl.host, accountSettings.credentials())
                .followRedirects(true)      // allow redirects
                .build()

            try {
                val response = client.okHttpClient.newCall(Request.Builder()
                        .get()
                        .url(httpUrl)
                        .build()).execute()

                if (response.isSuccessful)
                    return response.body?.bytes()
                else
                    logger.warning("Couldn't download external resource")
            } catch(e: IOException) {
                logger.log(Level.SEVERE, "Couldn't download external resource", e)
            } finally {
                client.close()
            }
            return null
        }
    }

    override fun notifyInvalidResourceTitle(): String =
            context.getString(R.string.sync_invalid_contact)

}
