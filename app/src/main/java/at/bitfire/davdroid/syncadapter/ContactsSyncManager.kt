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
import android.support.v4.app.NotificationCompat
import at.bitfire.dav4android.DavAddressBook
import at.bitfire.dav4android.DavResource
import at.bitfire.dav4android.exception.DavException
import at.bitfire.dav4android.property.*
import at.bitfire.davdroid.AccountSettings
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.*
import at.bitfire.davdroid.settings.ISettings
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.vcard4android.BatchOperation
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.GroupMethod
import ezvcard.VCardVersion
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import java.io.*
import java.util.*
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
 *   [android.provider.ContactsContract.CommonDataKinds.GroupMembership]. DAVdroid will
 *   then have to check whether the group memberships have actually changed, and if so,
 *   all affected groups have to be set to dirty. To detect changes in group memberships,
 *   DAVdroid always mirrors all [android.provider.ContactsContract.CommonDataKinds.GroupMembership]
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
        settings: ISettings,
        account: Account,
        accountSettings: AccountSettings,
        extras: Bundle,
        authority: String,
        syncResult: SyncResult,
        val provider: ContentProviderClient,
        localAddressBook: LocalAddressBook
): BaseDavSyncManager<LocalAddress, LocalAddressBook, DavAddressBook>(context, settings, account, accountSettings, extras, authority, syncResult, localAddressBook) {

    companion object {
        private const val MULTIGET_MAX_RESOURCES = 10

        infix fun <T> Set<T>.disjunct(other: Set<T>) = (this - other) union (other - this)
    }

    private val readOnly = localAddressBook.readOnly
    private var numDiscarded = 0

    private var hasVCard4 = false
    private val groupMethod = accountSettings.getGroupMethod()


    override fun prepare(): Boolean {
        if (!super.prepare())
            return false

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

        return true
    }

    override fun queryCapabilities() {
        useRemoteCollection { dav ->
            dav.propfind(0, SupportedAddressData.NAME, GetCTag.NAME, SyncToken.NAME)

            val properties = dav.properties
            properties[SupportedAddressData::class.java]?.let {
                hasVCard4 = it.hasVCard4()
            }
            Logger.log.info("Server advertises VCard/4 support: $hasVCard4")

            Logger.log.info("Contact group method: $groupMethod")
            // in case of GROUP_VCARDs, treat groups as contacts in the local address book
            localCollection.includeGroups = groupMethod == GroupMethod.GROUP_VCARDS
        }
    }

    override fun syncAlgorithm() = SyncAlgorithm.PROPFIND_REPORT

    override fun processLocallyDeleted(): Boolean {
        if (readOnly) {
            for (group in localCollection.findDeletedGroups()) {
                Logger.log.warning("Restoring locally deleted group (read-only address book!)")
                useLocal(group, { it.resetDeleted() })
                numDiscarded++
            }

            for (contact in localCollection.findDeletedContacts()) {
                Logger.log.warning("Restoring locally deleted contact (read-only address book!)")
                useLocal(contact, { it.resetDeleted() })
                numDiscarded++
            }

            if (numDiscarded > 0)
                notifyDiscardedChange()
            return false
        } else
            // mirror deletions to remote collection (DELETE)
            return super.processLocallyDeleted()
    }

    override fun uploadDirty(): Boolean {
        if (readOnly) {
            for (group in localCollection.findDirtyGroups()) {
                Logger.log.warning("Resetting locally modified group to ETag=null (read-only address book!)")
                useLocal(group, { it.clearDirty(null) })
                numDiscarded++
            }

            for (contact in localCollection.findDirtyContacts()) {
                Logger.log.warning("Resetting locally modified contact to ETag=null (read-only address book!)")
                useLocal(contact, { it.clearDirty(null) })
                numDiscarded++
            }

            if (numDiscarded > 0)
                notifyDiscardedChange()

        } else {
            if (groupMethod == GroupMethod.CATEGORIES) {
                /* groups memberships are represented as contact CATEGORIES */

                // groups with DELETED=1: set all members to dirty, then remove group
                for (group in localCollection.findDeletedGroups()) {
                    Logger.log.fine("Finally removing group $group")
                    // useless because Android deletes group memberships as soon as a group is set to DELETED:
                    // group.markMembersDirty()
                    useLocal(group, { it.delete() })
                }

                // groups with DIRTY=1: mark all memberships as dirty, then clean DIRTY flag of group
                for (group in localCollection.findDirtyGroups()) {
                    Logger.log.fine("Marking members of modified group $group as dirty")
                    useLocal(group, {
                        it.markMembersDirty()
                        it.clearDirty(null)
                    })
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

    private fun notifyDiscardedChange() {
        val notification = NotificationUtils.newBuilder(context, NotificationUtils.CHANNEL_SYNC_STATUS)
                .setSmallIcon(R.drawable.ic_delete_notification)
                .setContentTitle(context.getString(R.string.sync_contacts_read_only_address_book))
                .setContentText(context.resources.getQuantityString(R.plurals.sync_contacts_local_contact_changes_discarded, numDiscarded, numDiscarded))
                .setNumber(numDiscarded)
                .setSubText(account.name)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setLocalOnly(true)
                .build()
        notificationManager.notify("discarded_${account.name}", 0, notification)
    }

    override fun prepareUpload(resource: LocalAddress): RequestBody = useLocal(resource, {
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
    })

    override fun listAllRemote() = useRemoteCollection { dav ->
        // fetch list of remote VCards and build hash table to index file name
        dav.propfind(1, ResourceType.NAME, GetETag.NAME)

        val result = LinkedHashMap<String, DavResource>(dav.members.size)
        for (vCard in dav.members) {
            // ignore member collections
            var ignore = false
            vCard.properties[ResourceType::class.java]?.let { type ->
                if (type.types.contains(ResourceType.COLLECTION))
                    ignore = true
            }
            if (ignore)
                continue

            val fileName = vCard.fileName()
            Logger.log.fine("Found remote VCard: $fileName")
            result[fileName] = vCard
        }
        result
    }

    override fun processRemoteChanges(changes: RemoteChanges) {
        for (name in changes.deleted)
            localCollection.findByName(name)?.let {
                Logger.log.info("Deleting local address $name")
                useLocal(it, { it.delete() })
                syncResult.stats.numDeletes++
            }

        val toDownload = changes.updated.map { it.location }
        Logger.log.info("Downloading ${toDownload.size} resources ($MULTIGET_MAX_RESOURCES at once)")

        // prepare downloader which may be used to download external resource like contact photos
        val downloader = ResourceDownloader(collectionURL)

        // download new/updated VCards from server
        for (bunch in toDownload.chunked(CalendarSyncManager.MULTIGET_MAX_RESOURCES)) {
            if (bunch.size == 1)
                // only one contact, use GET
                useRemote(DavResource(httpClient.okHttpClient, bunch.first()), { remote ->
                    val body = remote.get("text/vcard;version=4.0, text/vcard;charset=utf-8;q=0.8, text/vcard;q=0.5")

                    // CardDAV servers MUST return ETag on GET [https://tools.ietf.org/html/rfc6352#section-6.3.2.3]
                    val eTag = remote.properties[GetETag::class.java]?.eTag
                            ?: throw DavException("Received CardDAV GET response without ETag for ${remote.location}")

                    body.charStream().use { reader ->
                        processVCard(remote.fileName(), eTag, reader, downloader)
                    }
                })

            else {
                // multiple contacts, use multi-get
                useRemoteCollection { it.multiget(bunch, hasVCard4) }

                // process multi-get results
                for (remote in davCollection.members)
                    useRemote(remote, {
                        val eTag = remote.properties[GetETag::class.java]?.eTag
                                ?: throw DavException("Received multi-get response without ETag")

                        val addressData = remote.properties[AddressData::class.java]
                        val vCard = addressData?.vCard
                                ?: throw DavException("Received multi-get response without address data")

                        processVCard(remote.fileName(), eTag, StringReader(vCard), downloader)
                    })
            }

            abortIfCancelled()
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
        val contacts = Contact.fromReader(reader, downloader)
        if (contacts.isEmpty()) {
            Logger.log.warning("Received VCard without data, ignoring")
            return
        } else if (contacts.size > 1)
            Logger.log.warning("Received multiple VCards, using first one")

        val newData = contacts.first()

        if (groupMethod == GroupMethod.CATEGORIES && newData.group) {
            Logger.log.warning("Received group VCard although group method is CATEGORIES. Saving as regular contact")
            newData.group = false
        }

        // update local contact, if it exists
        useLocal(localCollection.findByName(fileName), {
            var local = it
            if (local != null) {
                Logger.log.log(Level.INFO, "Updating $fileName in local address book", newData)

                if (local is LocalGroup && newData.group) {
                    // update group
                    local.eTag = eTag
                    local.updateFromServer(newData)
                    syncResult.stats.numUpdates++

                } else if (local is LocalContact && !newData.group) {
                    // update contact
                    local.eTag = eTag
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
                    useLocal(LocalGroup(localCollection, newData, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT), {
                        it.create()
                        local = it
                    })
                } else {
                    Logger.log.log(Level.INFO, "Creating local contact", newData)
                    useLocal(LocalContact(localCollection, newData, fileName, eTag, LocalResource.FLAG_REMOTELY_PRESENT), {
                        it.create()
                        local = it
                    })
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
        })
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

            val host = httpUrl.host()
            if (host == null) {
                Logger.log.log(Level.SEVERE, "External resource URL doesn't specify a host name", url)
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

}
