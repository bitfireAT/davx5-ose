/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Parcel
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import android.provider.ContactsContract.RawContacts.Data
import at.bitfire.vcard4android.*
import org.apache.commons.lang3.StringUtils
import java.util.*

class LocalGroup: AndroidGroup, LocalAddress {

    companion object {

        const val COLUMN_FLAGS = Groups.SYNC4

        /** marshaled list of member UIDs, as sent by server */
        const val COLUMN_PENDING_MEMBERS = Groups.SYNC3

        /**
         * Processes all groups with non-null {@link #COLUMN_PENDING_MEMBERS}: the pending memberships
         * are (if possible) applied, keeping cached memberships in sync.
         * @param addressBook    address book to take groups from
         */
        fun applyPendingMemberships(addressBook: LocalAddressBook) {
            addressBook.provider!!.query(
                    addressBook.groupsSyncUri(),
                    arrayOf(Groups._ID, COLUMN_PENDING_MEMBERS),
                    "$COLUMN_PENDING_MEMBERS IS NOT NULL", null,
                    null
            )?.use { cursor ->
                val batch = BatchOperation(addressBook.provider)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    Constants.log.fine("Assigning members to group $id")

                    // required for workaround for Android 7 which sets DIRTY flag when only meta-data is changed
                    val changeContactIDs = HashSet<Long>()

                    // delete all memberships and cached memberships for this group
                    for (contact in addressBook.getByGroupMembership(id)) {
                        contact.removeGroupMemberships(batch)
                        changeContactIDs += contact.id!!
                    }

                    // extract list of member UIDs
                    val members = LinkedList<String>()
                    val raw = cursor.getBlob(1)
                    val parcel = Parcel.obtain()
                    try {
                        parcel.unmarshall(raw, 0, raw.size)
                        parcel.setDataPosition(0)
                        parcel.readStringList(members)
                    } finally {
                        parcel.recycle()
                    }

                    // insert memberships
                    for (uid in members) {
                        Constants.log.fine("Assigning member: $uid")
                        addressBook.findContactByUID(uid)?.let { member ->
                            member.addToGroup(batch, id)
                            changeContactIDs += member.id!!
                        } ?: Constants.log.warning("Group member not found: $uid")
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
                        // workaround for Android 7 which sets DIRTY flag when only meta-data is changed
                        changeContactIDs
                                .map { addressBook.findContactByID(it) }
                                .forEach { it.updateHashCode(batch) }

                    // remove pending memberships
                    batch.enqueue(BatchOperation.CpoBuilder
                            .newUpdate(addressBook.syncAdapterURI(ContentUris.withAppendedId(Groups.CONTENT_URI, id)))
                            .withValue(COLUMN_PENDING_MEMBERS, null))

                    batch.commit()
                }
            }
        }

    }


    override var scheduleTag: String?
        get() = null
        set(value) = throw NotImplementedError()

    override var flags: Int = 0


    constructor(addressBook: AndroidAddressBook<out AndroidContact, LocalGroup>, values: ContentValues)
        : super(addressBook, values) {
        flags = values.getAsInteger(COLUMN_FLAGS) ?: 0
    }

    constructor(addressBook: AndroidAddressBook<out AndroidContact, LocalGroup>, contact: Contact, fileName: String?, eTag: String?, flags: Int)
        : super(addressBook, contact, fileName, eTag) {
        this.flags = flags
    }


    override fun contentValues(): ContentValues  {
        val values = super.contentValues()
        values.put(COLUMN_FLAGS, flags)

        val members = Parcel.obtain()
        try {
            members.writeStringList(contact!!.members)
            values.put(COLUMN_PENDING_MEMBERS, members.marshall())
        } finally {
            members.recycle()
        }
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

            contact!!.uid = uid
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
        values.put(COLUMN_ETAG, eTag)
        values.put(Groups.DIRTY, 0)
        update(values)

        if (fileName != null)
            this.fileName = fileName
        this.eTag = eTag

        // update cached group memberships
        val batch = BatchOperation(addressBook.provider!!)

        // delete cached group memberships
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


    // factory

    object Factory: AndroidGroupFactory<LocalGroup> {
        override fun fromProvider(addressBook: AndroidAddressBook<out AndroidContact, LocalGroup>, values: ContentValues) =
                LocalGroup(addressBook, values)
    }

}
