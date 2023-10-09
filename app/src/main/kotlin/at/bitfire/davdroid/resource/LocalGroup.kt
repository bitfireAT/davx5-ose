/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.resource

import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import android.provider.ContactsContract.RawContacts.Data
import at.bitfire.davdroid.log.Logger
import at.bitfire.vcard4android.*
import org.apache.commons.lang3.StringUtils
import java.util.*

class LocalGroup: AndroidGroup, LocalAddress {

    companion object {

        const val COLUMN_FLAGS = Groups.SYNC4

        /** List of member UIDs, as sent by server. This list will be used to establish
         *  the group memberships when all groups and contacts have been synchronized.
         *  Use [PendingMemberships] to create/read the list. */
        const val COLUMN_PENDING_MEMBERS = Groups.SYNC3

        /**
         * Processes all groups with non-null [COLUMN_PENDING_MEMBERS]: the pending memberships
         * are applied (if possible) to keep cached memberships in sync.
         *
         * @param addressBook    address book to take groups from
         */
        fun applyPendingMemberships(addressBook: LocalAddressBook) {
            Logger.log.info("Assigning memberships of contact groups")

            addressBook.allGroups { group ->
                val groupId = group.id!!
                val pendingMemberUids = group.pendingMemberships.toMutableSet()
                val batch = BatchOperation(addressBook.provider!!)

                // required for workaround for Android 7 which sets DIRTY flag when only meta-data is changed
                val changeContactIDs = HashSet<Long>()

                // process members which are currently in this group, but shouldn't be
                for (currentMemberId in addressBook.getContactIdsByGroupMembership(groupId)) {
                    val uid = addressBook.getContactUidFromId(currentMemberId) ?: continue

                    if (!pendingMemberUids.contains(uid)) {
                        Logger.log.fine("$currentMemberId removed from group $groupId; removing group membership")
                        val currentMember = addressBook.findContactById(currentMemberId)
                        currentMember.removeGroupMemberships(batch)

                        // Android 7 hack
                        changeContactIDs += currentMemberId
                    }

                    // UID is processed, remove from pendingMembers
                    pendingMemberUids -= uid
                }
                // now pendingMemberUids contains all UIDs which are not assigned yet

                // process members which should be in this group, but aren't
                for (missingMemberUid in pendingMemberUids) {
                    val missingMember = addressBook.findContactByUid(missingMemberUid)
                    if (missingMember == null) {
                        Logger.log.warning("Group $groupId has member $missingMemberUid which is not found in the address book; ignoring")
                        continue
                    }

                    Logger.log.fine("Assigning member $missingMember to group $groupId")
                    missingMember.addToGroup(batch, groupId)

                    // Android 7 hack
                    changeContactIDs += missingMember.id!!
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
                    // workaround for Android 7 which sets DIRTY flag when only meta-data is changed
                    changeContactIDs
                            .map { addressBook.findContactById(it) }
                            .forEach { it.updateHashCode(batch) }

                batch.commit()
            }
        }

    }


    override var scheduleTag: String?
        get() = null
        set(value) = throw NotImplementedError()

    override var flags: Int = 0

    var pendingMemberships = setOf<String>()


    constructor(addressBook: AndroidAddressBook<out AndroidContact, LocalGroup>, values: ContentValues) : super(addressBook, values) {
        flags = values.getAsInteger(COLUMN_FLAGS) ?: 0
        values.getAsString(COLUMN_PENDING_MEMBERS)?.let { members ->
            pendingMemberships = PendingMemberships.fromString(members).uids
        }
    }

    constructor(addressBook: AndroidAddressBook<out AndroidContact, LocalGroup>, contact: Contact, fileName: String?, eTag: String?, flags: Int)
        : super(addressBook, contact, fileName, eTag) {
        this.flags = flags
    }


    override fun contentValues(): ContentValues  {
        val values = super.contentValues()
        values.put(COLUMN_FLAGS, flags)
        values.put(COLUMN_PENDING_MEMBERS, PendingMemberships(getContact().members).toString())
        return values
    }


    override fun prepareForUpload(): String {
        var uid: String? = null
        addressBook.provider!!.query(groupSyncUri(), arrayOf(AndroidContact.COLUMN_UID), null, null, null)?.use { cursor ->
            if (cursor.moveToNext())
                uid = StringUtils.trimToNull(cursor.getString(0))
        }

        if (uid == null) {
            // generate new UID
            uid = UUID.randomUUID().toString()

            val values = ContentValues(1)
            values.put(AndroidContact.COLUMN_UID, uid)
            addressBook.provider!!.update(groupSyncUri(), values, null, null)

            _contact?.uid = uid
        }

        return "$uid.vcf"
    }

    override fun clearDirty(fileName: String?, eTag: String?, scheduleTag: String?) {
        if (scheduleTag != null)
            throw IllegalArgumentException("Contact groups must not have a Schedule-Tag")
        val id = requireNotNull(id)

        val values = ContentValues(3)
        if (fileName != null)
            values.put(COLUMN_FILENAME, fileName)
        values.putNull(COLUMN_ETAG)     // don't save changed ETag but null, so that the group is downloaded again, so that pendingMembers is updated
        values.put(Groups.DIRTY, 0)
        update(values)

        if (fileName != null)
            this.fileName = fileName
        this.eTag = null

        // update cached group memberships
        val batch = BatchOperation(addressBook.provider!!)

        // delete old cached group memberships
        batch.enqueue(BatchOperation.CpoBuilder
                .newDelete(addressBook.syncAdapterURI(ContactsContract.Data.CONTENT_URI))
                .withSelection(
                        CachedGroupMembership.MIMETYPE + "=? AND " + CachedGroupMembership.GROUP_ID + "=?",
                        arrayOf(CachedGroupMembership.CONTENT_ITEM_TYPE, id.toString())
                ))

        // insert updated cached group memberships
        for (member in getMembers())
            batch.enqueue(BatchOperation.CpoBuilder
                    .newInsert(addressBook.syncAdapterURI(ContactsContract.Data.CONTENT_URI))
                    .withValue(CachedGroupMembership.MIMETYPE, CachedGroupMembership.CONTENT_ITEM_TYPE)
                    .withValue(CachedGroupMembership.RAW_CONTACT_ID, member)
                    .withValue(CachedGroupMembership.GROUP_ID, id))

        batch.commit()
    }

    /**
     * Marks all members of the current group as dirty.
     */
    fun markMembersDirty() {
        val batch = BatchOperation(addressBook.provider!!)

        for (member in getMembers())
            batch.enqueue(BatchOperation.CpoBuilder
                    .newUpdate(addressBook.syncAdapterURI(ContentUris.withAppendedId(RawContacts.CONTENT_URI, member)))
                    .withValue(RawContacts.DIRTY, 1))

        batch.commit()
    }

    override fun resetDeleted() {
        val values = ContentValues(1)
        values.put(Groups.DELETED, 0)
        addressBook.provider!!.update(groupSyncUri(), values, null, null)
    }

    override fun updateFlags(flags: Int) {
        val values = ContentValues(1)
        values.put(COLUMN_FLAGS, flags)
        addressBook.provider!!.update(groupSyncUri(), values, null, null)

        this.flags = flags
    }


    // helpers

    private fun groupSyncUri(): Uri {
        val id = requireNotNull(id)
        return ContentUris.withAppendedId(addressBook.groupsSyncUri(), id)
    }

    /**
     * Lists all members of this group.
     * @return list of all members' raw contact IDs
     * @throws RemoteException on contact provider errors
     */
    internal fun getMembers(): List<Long> {
        val id = requireNotNull(id)
        val members = LinkedList<Long>()
        addressBook.provider!!.query(
                addressBook.syncAdapterURI(ContactsContract.Data.CONTENT_URI),
                arrayOf(Data.RAW_CONTACT_ID),
                "${GroupMembership.MIMETYPE}=? AND ${GroupMembership.GROUP_ROW_ID}=?",
                arrayOf(GroupMembership.CONTENT_ITEM_TYPE, id.toString()),
                null
        )?.use { cursor ->
            while (cursor.moveToNext())
                members += cursor.getLong(0)
        }
        return members
    }


    // helper class for COLUMN_PENDING_MEMBERSHIPS blob

    class PendingMemberships(
        /** list of member UIDs that shall be assigned **/
        val uids: Set<String>
    ) {

        companion object {
            const val SEPARATOR = '\n'

            fun fromString(value: String) =
                PendingMemberships(value.split(SEPARATOR).toSet())
        }

        override fun toString() = uids.joinToString(SEPARATOR.toString())

    }


    // factory

    object Factory: AndroidGroupFactory<LocalGroup> {
        override fun fromProvider(addressBook: AndroidAddressBook<out AndroidContact, LocalGroup>, values: ContentValues) =
                LocalGroup(addressBook, values)
    }

}