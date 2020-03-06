/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.*
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract.Groups
import at.bitfire.dav4jvm.DavAddressBook
import at.bitfire.dav4jvm.DavResponseCallback
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.property.*
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.SyncState
import at.bitfire.davdroid.resource.*
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.vcard4android.BatchOperation
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.GroupMethod
import ezvcard.VCardVersion
import ezvcard.io.CannotParseException
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import java.io.*
import java.util.logging.Level

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
class ContactsSyncManager(
        context: Context,
        account: Account,
        accountSettings: AccountSettings,
        extras: Bundle,
        authority: String,
        syncResult: SyncResult,
        val provider: ContentProviderClient,
        localAddressBook: LocalAddressBook
): SyncManager<LocalAddress, LocalAddressBook, DavAddressBook>(context, account, accountSettings, extras, authority, syncResult, localAddressBook) {

    companion object {
        infix fun <T> Set<T>.disjunct(other: Set<T>) = (this - other) union (other - this)
    }

    private val readOnly = localAddressBook.readOnly

    private var hasVCard4 = false
    private val groupMethod = accountSettings.getGroupMethod()

    /**
     * Used to download images which are referenced by URL
     */
    private lateinit var resourceDownloader: ResourceDownloader


    override fun prepare(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // workaround for Android 7 which sets DIRTY flag when only meta-data is changed
            val reallyDirty = localCollection.verifyDirty()
            val deleted = localCollection.findDeleted().size
            if (extras.containsKey(ContentResolver.SYNC_EXTRAS_UPLOAD) && reallyDirty == 0 && deleted == 0) {
                Logger.log.info("This sync was called to up-sync dirty/deleted contacts, but no contacts have been changed")
                return false
            }
        }

        collectionURL = HttpUrl.parse(localCollection.url) ?: return false
        davCollection = DavAddressBook(httpClient.okHttpClient, collectionURL)

        resourceDownloader = ResourceDownloader(davCollection.location)

        return true
    }

    override fun queryCapabilities(): SyncState? {
        Logger.log.info("Contact group method: $groupMethod")
        // in case of GROUP_VCARDs, treat groups as contacts in the local address book
        localCollection.includeGroups = groupMethod == GroupMethod.GROUP_VCARDS

        return useRemoteCollection {
            var syncState: SyncState? = null
            it.propfind(0, SupportedAddressData.NAME, SupportedReportSet.NAME, GetCTag.NAME, SyncToken.NAME) { response, relation ->
                if (relation == Response.HrefRelation.SELF) {
                    response[SupportedAddressData::class.java]?.let { supported ->
                        hasVCard4 = supported.hasVCard4()
                    }

                    response[SupportedReportSet::class.java]?.let { supported ->
                        hasCollectionSync = supported.reports.contains(SupportedReportSet.SYNC_COLLECTION)
                    }

                    syncState = syncState(response)
                }
            }

            Logger.log.info("Server supports vCard/4: $hasVCard4")
            Logger.log.info("Server supports Collection Sync: $hasCollectionSync")

            syncState
        }
    }

    override fun syncAlgorithm() = if (hasCollectionSync)
                SyncAlgorithm.COLLECTION_SYNC
            else
                SyncAlgorithm.PROPFIND_REPORT

    override fun processLocallyDeleted() =
            if (readOnly) {
                for (group in localCollection.findDeletedGroups()) {
                    Logger.log.warning("Restoring locally deleted group (read-only address book!)")
                    useLocal(group) { it.resetDeleted() }
                }

                for (contact in localCollection.findDeletedContacts()) {
                    Logger.log.warning("Restoring locally deleted contact (read-only address book!)")
                    useLocal(contact) { it.resetDeleted() }
                }

                false
            } else
                // mirror deletions to remote collection (DELETE)
                super.processLocallyDeleted()

    override fun uploadDirty(): Boolean {
        if (readOnly) {
            for (group in localCollection.findDirtyGroups()) {
                Logger.log.warning("Resetting locally modified group to ETag=null (read-only address book!)")
                useLocal(group) { it.clearDirty(null) }
            }

            for (contact in localCollection.findDirtyContacts()) {
                Logger.log.warning("Resetting locally modified contact to ETag=null (read-only address book!)")
                useLocal(contact) { it.clearDirty(null) }
            }

        } else {
            if (groupMethod == GroupMethod.CATEGORIES) {
                /* groups memberships are represented as contact CATEGORIES */

                // groups with DELETED=1: set all members to dirty, then remove group
                for (group in localCollection.findDeletedGroups()) {
                    Logger.log.fine("Finally removing group $group")
                    // useless because Android deletes group memberships as soon as a group is set to DELETED:
                    // group.markMembersDirty()
                    useLocal(group) { it.delete() }
                }

                // groups with DIRTY=1: mark all memberships as dirty, then clean DIRTY flag of group
                for (group in localCollection.findDirtyGroups()) {
                    Logger.log.fine("Marking members of modified group $group as dirty")
                    useLocal(group) {
                        it.markMembersDirty()
                        it.clearDirty(null)
                    }
                }
            } else {
                /* groups as separate VCards: there are group contacts and individual contacts */

                // mark groups with changed members as dirty
                val batch = BatchOperation(localCollection.provider!!)
                for (contact in localCollection.findDirtyContacts())
                    try {
                        Logger.log.fine("Looking for changed group memberships of contact ${contact.fileName}")
                        val cachedGroups = contact.getCachedGroupMemberships()
                        val currentGroups = contact.getGroupMemberships()
                        for (groupID in cachedGroups disjunct currentGroups) {
                            Logger.log.fine("Marking group as dirty: $groupID")
                            batch.enqueue(BatchOperation.Operation(
                                    ContentProviderOperation.newUpdate(localCollection.syncAdapterURI(ContentUris.withAppendedId(Groups.CONTENT_URI, groupID)))
                                    .withValue(Groups.DIRTY, 1)
                                    .withYieldAllowed(true)
                            ))
                        }
                    } catch(e: FileNotFoundException) {
                    }
                batch.commit()
            }
        }

        // generate UID/file name for newly created contacts
        return super.uploadDirty()
    }

    override fun prepareUpload(resource: LocalAddress): RequestBody = useLocal(resource) {
        val contact: Contact
        if (resource is LocalContact) {
            contact = resource.contact!!

            if (groupMethod == GroupMethod.CATEGORIES) {
                // add groups as CATEGORIES
                for (groupID in resource.getGroupMemberships()) {
                    provider.query(
                            localCollection.syncAdapterURI(ContentUris.withAppendedId(Groups.CONTENT_URI, groupID)),
                            arrayOf(Groups.TITLE), null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToNext()) {
                            val title = cursor.getString(0)
                            if (!title.isNullOrEmpty())
                                contact.categories.add(title)
                        }
                    }
                }
            }
        } else if (resource is LocalGroup)
            contact = resource.contact!!
        else
            throw IllegalArgumentException("resource must be LocalContact or LocalGroup")

        Logger.log.log(Level.FINE, "Preparing upload of VCard ${resource.fileName}", contact)

        val os = ByteArrayOutputStream()
        contact.write(if (hasVCard4) VCardVersion.V4_0 else VCardVersion.V3_0, groupMethod, os)

        RequestBody.create(
                if (hasVCard4) DavAddressBook.MIME_VCARD4 else DavAddressBook.MIME_VCARD3_UTF8,
                os.toByteArray()
        )
    }

    override fun listAllRemote(callback: DavResponseCallback) =
            useRemoteCollection {
                it.propfind(1, ResourceType.NAME, GetETag.NAME, callback = callback)
            }

    override fun downloadRemote(bunch: List<HttpUrl>) {
        Logger.log.info("Downloading ${bunch.size} vCard(s): $bunch")
        useRemoteCollection {
            it.multiget(bunch, hasVCard4) { response, _ ->
                useRemote(response) {
                    if (!response.isSuccess()) {
                        Logger.log.warning("Received non-successful multiget response for ${response.href}")
                        return@useRemote
                    }

                    val eTag = response[GetETag::class.java]?.eTag
                            ?: throw DavException("Received multi-get response without ETag")

                    val addressData = response[AddressData::class.java]
                    val vCard = addressData?.vCard
                            ?: throw DavException("Received multi-get response without address data")

                    processVCard(DavUtils.lastSegmentOfUrl(response.href), eTag, StringReader(vCard), resourceDownloader)
                }
            }
        }
    }

    override fun postProcess() {
        if (groupMethod == GroupMethod.CATEGORIES) {
            /* VCard3 group handling: groups memberships are represented as contact CATEGORIES */

            // remove empty groups
            Logger.log.info("Removing empty groups")
            localCollection.removeEmptyGroups()

        } else {
            /* VCard4 group handling: there are group contacts and individual contacts */
            Logger.log.info("Assigning memberships of downloaded contact groups")
            LocalGroup.applyPendingMemberships(localCollection)
        }
    }


    // helpers

    private fun processVCard(fileName: String, eTag: String, reader: Reader, downloader: Contact.Downloader) {
        Logger.log.info("Processing CardDAV resource $fileName")

        val contacts = try {
            Contact.fromReader(reader, downloader)
        } catch (e: CannotParseException) {
            Logger.log.log(Level.SEVERE, "Received invalid vCard, ignoring", e)
            notifyInvalidResource(e, fileName)
            return
        }

        if (contacts.isEmpty()) {
            Logger.log.warning("Received vCard without data, ignoring")
            return
        } else if (contacts.size > 1)
            Logger.log.warning("Received multiple vCards, using first one")

        val newData = contacts.first()

        if (groupMethod == GroupMethod.CATEGORIES && newData.group) {
            Logger.log.warning("Received group vCard although group method is CATEGORIES. Saving as regular contact")
            newData.group = false
        }

        // update local contact, if it exists
        useLocal(localCollection.findByName(fileName)) {
            var local = it
            if (local != null) {
                Logger.log.log(Level.INFO, "Updating $fileName in local address book", newData)

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
                    // group has become an individual contact or vice versa
                    local.delete()
                    local = null
                }
            }

            if (local == null) {
                if (newData.group) {
                    Logger.log.log(Level.INFO, "Creating local group", newData)
                    useLocal(LocalGroup(localCollection, newData, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT)) { group ->
                        group.add()
                        local = group
                    }
                } else {
                    Logger.log.log(Level.INFO, "Creating local contact", newData)
                    useLocal(LocalContact(localCollection, newData, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT)) { contact ->
                        contact.add()
                        local = contact
                    }
                }
                syncResult.stats.numInserts++
            }

            if (groupMethod == GroupMethod.CATEGORIES)
                (local as? LocalContact)?.let { localContact ->
                    // VCard3: update group memberships from CATEGORIES
                    val batch = BatchOperation(provider)
                    Logger.log.log(Level.FINE, "Removing contact group memberships")
                    localContact.removeGroupMemberships(batch)

                    for (category in localContact.contact!!.categories) {
                        val groupID = localCollection.findOrCreateGroup(category)
                        Logger.log.log(Level.FINE, "Adding membership in group $category ($groupID)")
                        localContact.addToGroup(batch, groupID)
                    }

                    batch.commit()
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            // workaround for Android 7 which sets DIRTY flag when only meta-data is changed
                (local as? LocalContact)?.updateHashCode(null)
        }
    }


    // downloader helper class

    private inner class ResourceDownloader(
            val baseUrl: HttpUrl
    ): Contact.Downloader {

        override fun download(url: String, accepts: String): ByteArray? {
            val httpUrl = HttpUrl.parse(url)
            if (httpUrl == null) {
                Logger.log.log(Level.SEVERE, "Invalid external resource URL", url)
                return null
            }

            // authenticate only against a certain host, and only upon request
            val builder = HttpClient.Builder(context, baseUrl.host(), accountSettings.credentials())

            // allow redirects
            builder.followRedirects(true)

            val client = builder.build()
            try {
                val response = client.okHttpClient.newCall(Request.Builder()
                        .get()
                        .url(httpUrl)
                        .build()).execute()

                if (response.isSuccessful)
                    return response.body()?.bytes()
                else
                    Logger.log.warning("Couldn't download external resource")
            } catch(e: IOException) {
                Logger.log.log(Level.SEVERE, "Couldn't download external resource", e)
            } finally {
                client.close()
            }
            return null
        }
    }

    override fun notifyInvalidResourceTitle(): String =
            context.getString(R.string.sync_invalid_contact)

}
