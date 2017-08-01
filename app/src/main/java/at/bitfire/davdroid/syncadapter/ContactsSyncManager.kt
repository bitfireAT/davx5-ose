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
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.Groups
import at.bitfire.dav4android.DavAddressBook
import at.bitfire.dav4android.DavResource
import at.bitfire.dav4android.exception.DavException
import at.bitfire.dav4android.property.*
import at.bitfire.davdroid.AccountSettings
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.resource.LocalContact
import at.bitfire.davdroid.resource.LocalGroup
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.vcard4android.BatchOperation
import at.bitfire.vcard4android.Contact
import at.bitfire.vcard4android.ContactsStorageException
import at.bitfire.vcard4android.GroupMethod
import ezvcard.VCardVersion
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody
import org.apache.commons.collections4.ListUtils
import java.io.*
import java.util.*
import java.util.logging.Level

/**
 * <p>Synchronization manager for CardDAV collections; handles contacts and groups.</p>
 *
 * <p></p>Group handling differs according to the {@link #groupMethod}. There are two basic methods to
 * handle/manage groups:</p>
 * <ul>
 *     <li>{@code CATEGORIES}: groups memberships are attached to each contact and represented as
 *     "category". When a group is dirty or has been deleted, all its members have to be set to
 *     dirty, too (because they have to be uploaded without the respective category). This
 *     is done in {@link #prepareDirty()}. Empty groups can be deleted without further processing,
 *     which is done in {@link #postProcess()} because groups may become empty after downloading
 *     updated remoted contacts.</li>
 *     <li>Groups as separate VCards: individual and group contacts (with a list of member UIDs) are
 *     distinguished. When a local group is dirty, its members don't need to be set to dirty.
 *     <ol>
 *         <li>However, when a contact is dirty, it has
 *         to be checked whether its group memberships have changed. In this case, the respective
 *         groups have to be set to dirty. For instance, if contact A is in group G and H, and then
 *         group membership of G is removed, the contact will be set to dirty because of the changed
 *         {@link android.provider.ContactsContract.CommonDataKinds.GroupMembership}. DAVdroid will
 *         then have to check whether the group memberships have actually changed, and if so,
 *         all affected groups have to be set to dirty. To detect changes in group memberships,
 *         DAVdroid always mirrors all {@link android.provider.ContactsContract.CommonDataKinds.GroupMembership}
 *         data rows in respective {@link at.bitfire.vcard4android.CachedGroupMembership} rows.
 *         If the cached group memberships are not the same as the current group member ships, the
 *         difference set (in our example G, because its in the cached memberships, but not in the
 *         actual ones) is marked as dirty. This is done in {@link #prepareDirty()}.</li>
 *         <li>When downloading remote contacts, groups (+ member information) may be received
 *         by the actual members. Thus, the member lists have to be cached until all VCards
 *         are received. This is done by caching the member UIDs of each group in
 *         {@link LocalGroup#COLUMN_PENDING_MEMBERS}. In {@link #postProcess()},
 *         these "pending memberships" are assigned to the actual contacs and then cleaned up.</li>
 *     </ol>
 * </ul>
 */
class ContactsSyncManager(
        context: Context,
        account: Account,
        settings: AccountSettings,
        extras: Bundle,
        authority: String,
        syncResult: SyncResult,
        val provider: ContentProviderClient,
        val localAddressBook: LocalAddressBook
): SyncManager(context, account, settings, extras, authority, syncResult, "addressBook") {

    val MAX_MULTIGET = 10

    private var hasVCard4 = false
    private lateinit var groupMethod: GroupMethod


    init {
        localCollection = localAddressBook
    }

    override fun notificationId() = Constants.NOTIFICATION_CONTACTS_SYNC

    override fun getSyncErrorTitle() = context.getString(R.string.sync_error_contacts, account.name)!!


    override fun prepare(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // workaround for Android 7 which sets DIRTY flag when only meta-data is changed
            val reallyDirty = localAddressBook.verifyDirty()
            val deleted = localAddressBook.getDeleted().size
            if (extras.containsKey(ContentResolver.SYNC_EXTRAS_UPLOAD) && reallyDirty == 0 && deleted == 0) {
                Logger.log.info("This sync was called to up-sync dirty/deleted contacts, but no contacts have been changed")
                return false
            }
        }

        // set up Contacts Provider Settings
        val values = ContentValues(2)
        values.put(ContactsContract.Settings.SHOULD_SYNC, 1)
        values.put(ContactsContract.Settings.UNGROUPED_VISIBLE, 1)
        localAddressBook.updateSettings(values)

        collectionURL = HttpUrl.parse(localAddressBook.getURL()) ?: return false
        davCollection = DavAddressBook(httpClient, collectionURL)

        return true
    }

    override fun queryCapabilities() {
        // prepare remote address book
        davCollection.propfind(0, SupportedAddressData.NAME, GetCTag.NAME)
        (davCollection.properties[SupportedAddressData.NAME] as SupportedAddressData?)?.let {
            hasVCard4 = it.hasVCard4()
        }
        Logger.log.info("Server advertises VCard/4 support: $hasVCard4")

        groupMethod = settings.getGroupMethod()
        Logger.log.info("Contact group method: $groupMethod")

        localAddressBook.includeGroups = groupMethod == GroupMethod.GROUP_VCARDS
    }

    override fun prepareDirty() {
        super.prepareDirty()

        if (groupMethod == GroupMethod.CATEGORIES) {
            /* groups memberships are represented as contact CATEGORIES */

            // groups with DELETED=1: set all members to dirty, then remove group
            for (group in localAddressBook.getDeletedGroups()) {
                Logger.log.fine("Finally removing group $group")
                // useless because Android deletes group memberships as soon as a group is set to DELETED:
                // group.markMembersDirty()
                group.delete()
            }

            // groups with DIRTY=1: mark all memberships as dirty, then clean DIRTY flag of group
            for (group in localAddressBook.getDirtyGroups()) {
                Logger.log.fine("Marking members of modified group $group as dirty")
                group.markMembersDirty()
                group.clearDirty(null)
            }
        } else {
            /* groups as separate VCards: there are group contacts and individual contacts */

            // mark groups with changed members as dirty
            val batch = BatchOperation(localAddressBook.provider!!)
            for (contact in localAddressBook.getDirtyContacts())
                try {
                    Logger.log.fine("Looking for changed group memberships of contact ${contact.fileName}")
                    val cachedGroups = contact.getCachedGroupMemberships()
                    val currentGroups = contact.getGroupMemberships()
                    for (groupID in cachedGroups disjunct currentGroups) {
                        Logger.log.fine("Marking group as dirty: $groupID")
                        batch.enqueue(BatchOperation.Operation(
                                ContentProviderOperation.newUpdate(localAddressBook.syncAdapterURI(ContentUris.withAppendedId(Groups.CONTENT_URI, groupID)))
                                .withValue(Groups.DIRTY, 1)
                                .withYieldAllowed(true)
                        ))
                    }
                } catch(e: FileNotFoundException) {
                }
            batch.commit()
        }
    }

    override fun prepareUpload(resource: LocalResource): RequestBody {
        val contact: Contact
        if (resource is LocalContact) {
            contact = resource.contact!!

            if (groupMethod == GroupMethod.CATEGORIES) {
                // add groups as CATEGORIES
                for (groupID in resource.getGroupMemberships()) {
                    try {
                        provider.query(
                                localAddressBook.syncAdapterURI(ContentUris.withAppendedId(Groups.CONTENT_URI, groupID)),
                                arrayOf(Groups.TITLE), null, null, null
                        )?.use { cursor ->
                            if (cursor.moveToNext()) {
                                val title = cursor.getString(0)
                                if (!title.isNullOrEmpty())
                                    contact.categories.add(title)
                            }
                        }
                    } catch(e: RemoteException) {
                        throw ContactsStorageException("Couldn't find group for adding CATEGORIES", e)
                    }
                }
            }
        } else if (resource is LocalGroup)
            contact = resource.contact!!
        else
            throw IllegalArgumentException("resource must be a LocalContact or a LocalGroup")

        Logger.log.log(Level.FINE, "Preparing upload of VCard ${resource.fileName}", contact)

        val os = ByteArrayOutputStream()
        contact.write(if (hasVCard4) VCardVersion.V4_0 else VCardVersion.V3_0, groupMethod, os)

        return RequestBody.create(
                if (hasVCard4) DavAddressBook.MIME_VCARD4 else DavAddressBook.MIME_VCARD3_UTF8,
                os.toByteArray()
        )
    }

    override fun listRemote() {
        val addressBook = davAddressBook()
        currentDavResource = addressBook

        // fetch list of remote VCards and build hash table to index file name
        addressBook.propfind(1, ResourceType.NAME, GetETag.NAME)

        remoteResources = HashMap<String, DavResource>(davCollection.members.size)
        for (vCard in davCollection.members) {
            // ignore member collections
            val type = vCard.properties[ResourceType.NAME] as ResourceType?
            if (type != null && type.types.contains(ResourceType.COLLECTION))
                continue

            val fileName = vCard.fileName()
            Logger.log.fine("Found remote VCard: $fileName")
            remoteResources[fileName] = vCard
        }

        currentDavResource = null
    }

    override fun downloadRemote() {
        Logger.log.info("Downloading ${toDownload.size} contacts ($MAX_MULTIGET at once)")

        // prepare downloader which may be used to download external resource like contact photos
        val downloader = ResourceDownloader(collectionURL)

        // download new/updated VCards from server
        for (bunch in ListUtils.partition(toDownload.toList(), MAX_MULTIGET)) {
            if (Thread.interrupted())
                return

            Logger.log.info("Downloading ${bunch.joinToString(", ")}")

            if (bunch.size == 1) {
                // only one contact, use GET
                val remote = bunch.first()
                currentDavResource = remote

                val body = remote.get("text/vcard;version=4.0, text/vcard;charset=utf-8;q=0.8, text/vcard;q=0.5")

                // CardDAV servers MUST return ETag on GET [https://tools.ietf.org/html/rfc6352#section-6.3.2.3]
                val eTag = remote.properties[GetETag.NAME] as GetETag?
                if (eTag == null || eTag.eTag.isNullOrEmpty())
                    throw DavException("Received CardDAV GET response without ETag for ${remote.location}")

                body.charStream().use { reader ->
                    processVCard(remote.fileName(), eTag.eTag!!, reader, downloader)
                }

            } else {
                // multiple contacts, use multi-get
                val addressBook = davAddressBook()
                currentDavResource = addressBook
                addressBook.multiget(bunch.map { it.location }, hasVCard4)

                // process multi-get results
                for (remote in davCollection.members) {
                    currentDavResource = remote

                    val eTag = (remote.properties[GetETag.NAME] as GetETag?)?.eTag
                            ?: throw DavException("Received multi-get response without ETag")

                    val addressData = remote.properties[AddressData.NAME] as AddressData?
                    val vCard = addressData?.vCard
                            ?: throw DavException("Received multi-get response without address data")

                    processVCard(remote.fileName(), eTag, StringReader(vCard), downloader)
                }
            }

            currentDavResource = null
        }
    }

    override fun postProcess() {
        if (groupMethod == GroupMethod.CATEGORIES) {
            /* VCard3 group handling: groups memberships are represented as contact CATEGORIES */

            // remove empty groups
            Logger.log.info("Removing empty groups")
            localAddressBook.removeEmptyGroups()

        } else {
            /* VCard4 group handling: there are group contacts and individual contacts */
            Logger.log.info("Assigning memberships of downloaded contact groups")
            LocalGroup.applyPendingMemberships(localAddressBook)
        }
    }


    // helpers

    private fun davAddressBook() = davCollection as DavAddressBook

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
            groupMethod = GroupMethod.GROUP_VCARDS
            Logger.log.warning("Received group VCard although group method is CATEGORIES. Deleting all groups; new group method: $groupMethod")
            localAddressBook.removeGroups()
            settings.setGroupMethod(groupMethod)
        }

        // update local contact, if it exists
        var local = localResources[fileName]
        currentLocalResource = local
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
                val group = LocalGroup(localAddressBook, newData, fileName, eTag)
                currentLocalResource = group
                group.create()

                local = group
            } else {
                Logger.log.log(Level.INFO, "Creating local contact", newData)
                val contact = LocalContact(localAddressBook, newData, fileName, eTag)
                currentLocalResource = contact
                contact.create()

                local = contact
            }
            syncResult.stats.numInserts++
        }

        if (groupMethod == GroupMethod.CATEGORIES && local is LocalContact) {
            // VCard3: update group memberships from CATEGORIES
            currentLocalResource = local

            val batch = BatchOperation(provider)
            Logger.log.log(Level.FINE, "Removing contact group memberships")
            local.removeGroupMemberships(batch)

            for (category in local.contact!!.categories) {
                val groupID = localAddressBook.findOrCreateGroup(category)
                Logger.log.log(Level.FINE, "Adding membership in group $category ($groupID)")
                local.addToGroup(batch, groupID)
            }

            batch.commit()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT < Build.VERSION_CODES.O && local is LocalContact)
            // workaround for Android 7 which sets DIRTY flag when only meta-data is changed
            local.updateHashCode(null)

        currentLocalResource = null
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

            var resourceClient = HttpClient.create(context)

            // authenticate only against a certain host, and only upon request
            val username = settings.username()
            val password = settings.password()
            if (username != null && password != null)
                resourceClient = HttpClient.addAuthentication(resourceClient, baseUrl.host(), username, password)

            // allow redirects
            resourceClient = resourceClient.newBuilder()
                    .followRedirects(true)
                    .build()

            try {
                val response = resourceClient.newCall(Request.Builder()
                        .get()
                        .url(httpUrl)
                        .build()).execute()

                if (response.isSuccessful)
                    return response.body()?.bytes()
                else
                    Logger.log.warning("Couldn't download external resource")
            } catch(e: IOException) {
                Logger.log.log(Level.SEVERE, "Couldn't download external resource", e)
            }
            return null
        }
    }

}
