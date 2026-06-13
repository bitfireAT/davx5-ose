/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import android.provider.ContactsContract.RawContacts.Data
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.mapping.contacts.PendingMemberships
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.contacts.AddressContract.CachedGroupMembership
import at.bitfire.synctools.storage.contacts.AddressContract.GroupColumns
import at.bitfire.synctools.storage.contacts.AddressContract.asSyncAdapter
import at.bitfire.synctools.storage.contacts.AndroidAddressBook
import at.bitfire.synctools.storage.contacts.AndroidContact
import at.bitfire.synctools.storage.contacts.AndroidGroup
import at.bitfire.synctools.storage.contacts.AndroidGroupFactory
import at.bitfire.synctools.storage.contacts.ContactsBatchOperation
import com.google.common.base.MoreObjects
import java.util.LinkedList
import java.util.Optional
import java.util.logging.Logger
import kotlin.jvm.optionals.getOrNull

class LocalGroup: AndroidGroup, LocalAddress {

    companion object {
        
        private val logger: Logger
            get() = Logger.getGlobal()

        /**
         * Processes all groups with non-null [GroupColumns.PENDING_MEMBERS]: the pending memberships
         * are applied (if possible) to keep cached memberships in sync.
         *
         * @param addressBook    address book to take groups from
         */
        fun applyPendingMemberships(addressBook: LocalAddressBook) {
            logger.info("Assigning memberships of contact groups")

            addressBook.allGroups { group ->
                val groupId = group.id!!
                val pendingMemberUids = group.pendingMemberships.toMutableSet()
                val batch = ContactsBatchOperation(addressBook.provider!!)

                // required for workaround for Android 7 which sets DIRTY flag when only meta-data is changed
                val changeContactIDs = HashSet<Long>()

                // process members which are currently in this group, but shouldn't be
                for (currentMemberId in addressBook.getContactIdsByGroupMembership(groupId)) {
                    val uid = addressBook.getContactUidFromId(currentMemberId) ?: continue

                    if (!pendingMemberUids.contains(uid)) {
                        logger.fine("$currentMemberId removed from group $groupId; removing group membership")
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
                        logger.warning("Group $groupId has member $missingMemberUid which is not found in the address book; ignoring")
                        continue
                    }

                    logger.fine("Assigning member $missingMember to group $groupId")
                    missingMember.addToGroup(batch, groupId)

                    // Android 7 hack
                    changeContactIDs += missingMember.id!!
                }

                addressBook.dirtyVerifier.getOrNull()?.let { verifier ->
                    // workaround for Android 7 which sets DIRTY flag when only meta-data is changed
                    changeContactIDs
                        .map { id -> addressBook.findContactById(id) }
                        .forEach { contact ->
                            verifier.updateHashCode(contact, batch)
                        }
                }

                batch.commit()
            }
        }

    }


    override var scheduleTag: String?
        get() = null
        set(_) = throw NotImplementedError()

    override var flags: Int = 0

    var pendingMemberships = setOf<String>()


    constructor(addressBook: AndroidAddressBook<out AndroidContact, LocalGroup>, values: ContentValues) : super(addressBook, values) {
        flags = values.getAsInteger(GroupColumns.FLAGS) ?: 0
        values.getAsString(GroupColumns.PENDING_MEMBERS)?.let { members ->
            pendingMemberships = PendingMemberships.fromString(members).uids
        }
    }

    constructor(addressBook: AndroidAddressBook<out AndroidContact, LocalGroup>, contact: Contact, fileName: String?, eTag: String?, flags: Int)
        : super(addressBook, contact, fileName, eTag) {
        this.flags = flags
    }


    override fun contentValues(): ContentValues  {
        val values = super.contentValues()
        values.put(GroupColumns.FLAGS, flags)
        values.put(GroupColumns.PENDING_MEMBERS, PendingMemberships(getContact().members).toString())
        return values
    }


    override fun clearDirty(fileName: Optional<String>, eTag: String?, scheduleTag: String?) {
        if (scheduleTag != null)
            throw IllegalArgumentException("Contact groups must not have a Schedule-Tag")
        val id = requireNotNull(id)

        val values = ContentValues(3)
        if (fileName.isPresent)
            values.put(COLUMN_FILENAME, fileName.get())
        values.putNull(COLUMN_ETAG)     // don't save changed ETag but null, so that the group is downloaded again, so that pendingMembers is updated
        values.put(Groups.DIRTY, 0)
        update(values)

        if (fileName.isPresent)
            this.fileName = fileName.get()
        this.eTag = null

        // update cached group memberships
        val batch = ContactsBatchOperation(addressBook.provider!!)

        // delete old cached group memberships
        batch += BatchOperation.CpoBuilder
            .newDelete(ContactsContract.Data.CONTENT_URI.asSyncAdapter())
            .withSelection(
                CachedGroupMembership.MIMETYPE + "=? AND " + CachedGroupMembership.GROUP_ID + "=?",
                arrayOf(CachedGroupMembership.CONTENT_ITEM_TYPE, id.toString())
            )

        // insert updated cached group memberships
        for (member in getMembers())
            batch += BatchOperation.CpoBuilder
                .newInsert(ContactsContract.Data.CONTENT_URI.asSyncAdapter())
                .withValue(CachedGroupMembership.MIMETYPE, CachedGroupMembership.CONTENT_ITEM_TYPE)
                .withValue(CachedGroupMembership.RAW_CONTACT_ID, member)
                .withValue(CachedGroupMembership.GROUP_ID, id)

        batch.commit()
    }

    /**
     * Marks all members of the current group as dirty.
     */
    fun markMembersDirty() {
        val batch = ContactsBatchOperation(addressBook.provider!!)

        for (member in getMembers())
            batch += BatchOperation.CpoBuilder
                .newUpdate(ContentUris.withAppendedId(RawContacts.CONTENT_URI, member).asSyncAdapter())
                .withValue(RawContacts.DIRTY, 1)

        batch.commit()
    }

    override fun update(data: Contact, fileName: String?, eTag: String?, scheduleTag: String?, flags: Int) {
        this.fileName = fileName
        this.eTag = eTag

        // processes this.{fileName, eTag, flags} and resets DIRTY flag
        update(data)
    }

    override fun updateFlags(flags: Int) {
        val values = contentValuesOf(GroupColumns.FLAGS to flags)
        addressBook.provider!!.update(groupSyncUri(), values, null, null)

        this.flags = flags
    }

    override fun updateSequence(sequence: Int) = throw NotImplementedError()

    override fun updateUid(uid: String) {
        val values = contentValuesOf(AndroidContact.COLUMN_UID to uid)
        addressBook.provider!!.update(groupSyncUri(), values, null, null)
    }

    override fun deleteLocal() {
        delete()
    }

    override fun resetDeleted() {
        val values = contentValuesOf(Groups.DELETED to 0)
        addressBook.provider!!.update(groupSyncUri(), values, null, null)
    }

    override fun getDebugSummary() =
        MoreObjects.toStringHelper(this)
            .add("id", id)
            .add("fileName", fileName)
            .add("eTag", eTag)
            .add("flags", flags)
            .add("contact",
                try {
                    getContact().toString()
                } catch (e: Exception) {
                    e
                }
            ).toString()

    override fun getViewUri(context: Context) = null


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
            ContactsContract.Data.CONTENT_URI.asSyncAdapter(),
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


    // factory

    object Factory: AndroidGroupFactory<LocalGroup> {
        override fun fromProvider(addressBook: AndroidAddressBook<out AndroidContact, LocalGroup>, values: ContentValues) =
                LocalGroup(addressBook, values)
    }

}