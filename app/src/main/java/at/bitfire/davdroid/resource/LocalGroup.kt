/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.os.Build
import android.os.Parcel
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import android.provider.ContactsContract.RawContacts.Data
import at.bitfire.dav4android.Constants
import at.bitfire.vcard4android.*
import java.io.FileNotFoundException
import java.util.*
import java.util.logging.Level

// TODO @ToString(callSuper=true)
class LocalGroup: AndroidGroup, LocalResource {

    companion object {

        /** marshaled list of member UIDs, as sent by server */
        val COLUMN_PENDING_MEMBERS = Groups.SYNC3

        /**
         * Processes all groups with non-null {@link #COLUMN_PENDING_MEMBERS}: the pending memberships
         * are (if possible) applied, keeping cached memberships in sync.
         * @param addressBook    address book to take groups from
         * @throws ContactsStorageException on contact provider errors
         */
        @JvmStatic
        @Throws(ContactsStorageException::class)
        fun applyPendingMemberships(addressBook: LocalAddressBook) {
            try {
                addressBook.provider!!.query(
                        addressBook.syncAdapterURI(Groups.CONTENT_URI),
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
                            changeContactIDs.add(contact.id!!)
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
                            try {
                                val member = addressBook.findContactByUID(uid)
                                member.addToGroup(batch, id)
                                changeContactIDs.add(member.id!!)
                            } catch(e: FileNotFoundException) {
                                Constants.log.log(Level.WARNING, "Group member not found: $uid", e)
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                            // workaround for Android 7 which sets DIRTY flag when only meta-data is changed
                            changeContactIDs
                                    .map { LocalContact(addressBook, it, null, null) }
                                    .forEach { it.updateHashCode(batch) }

                        // remove pending memberships
                        batch.enqueue(BatchOperation.Operation(
                                ContentProviderOperation.newUpdate(addressBook.syncAdapterURI(ContentUris.withAppendedId(Groups.CONTENT_URI, id)))
                                        .withValue(COLUMN_PENDING_MEMBERS, null)
                                        .withYieldAllowed(true)
                                ))

                        batch.commit()
                    }
                }
            } catch(e: RemoteException) {
                throw ContactsStorageException("Couldn't get pending memberships", e)
            }
        }

    }


    constructor(addressBook: AndroidAddressBook<out AndroidContact, LocalGroup>, id: Long, fileName: String?, eTag: String?):
        super(addressBook, id, fileName, eTag)

    constructor(addressBook: AndroidAddressBook<out AndroidContact, LocalGroup>, contact: Contact, fileName: String?, eTag: String?):
        super(addressBook, contact, fileName, eTag)


    @Throws(ContactsStorageException::class)
    override fun clearDirty(eTag: String?) {
        val id = requireNotNull(id)

        val values = ContentValues(2)
        values.put(Groups.DIRTY, 0)
        values.put(COLUMN_ETAG, eTag)
        this.eTag = eTag
        update(values)

        // update cached group memberships
        val batch = BatchOperation(addressBook.provider!!)

        // delete cached group memberships
        batch.enqueue(BatchOperation.Operation(
                ContentProviderOperation.newDelete(addressBook.syncAdapterURI(ContactsContract.Data.CONTENT_URI))
                        .withSelection(
                                CachedGroupMembership.MIMETYPE + "=? AND " + CachedGroupMembership.GROUP_ID + "=?",
                                arrayOf(CachedGroupMembership.CONTENT_ITEM_TYPE, id.toString())
                        )
        ))

        // insert updated cached group memberships
        for (member in getMembers())
            batch.enqueue(BatchOperation.Operation(
                    ContentProviderOperation.newInsert(addressBook.syncAdapterURI(ContactsContract.Data.CONTENT_URI))
                            .withValue(CachedGroupMembership.MIMETYPE, CachedGroupMembership.CONTENT_ITEM_TYPE)
                            .withValue(CachedGroupMembership.RAW_CONTACT_ID, member)
                            .withValue(CachedGroupMembership.GROUP_ID, id)
                            .withYieldAllowed(true)
            ))

        batch.commit()
    }

    @Throws(ContactsStorageException::class)
    override fun prepareForUpload() {
        val uid = UUID.randomUUID().toString()
        val newFileName = "$uid.vcf"

        val values = ContentValues(2)
        values.put(COLUMN_FILENAME, newFileName)
        values.put(COLUMN_UID, uid)
        update(values)

        fileName = newFileName
    }

    @Throws(FileNotFoundException::class, ContactsStorageException::class)
    override fun contentValues(): ContentValues  {
        val values = super.contentValues()

        val members = Parcel.obtain()
        try {
            members.writeStringList(contact!!.members)
            values.put(COLUMN_PENDING_MEMBERS, members.marshall())
        } finally {
            members.recycle()
        }
        return values
    }


    /**
     * Marks all members of the current group as dirty.
     */
    @Throws(ContactsStorageException::class)
    fun markMembersDirty() {
        val batch = BatchOperation(addressBook.provider!!)

        for (member in getMembers())
            batch.enqueue(BatchOperation.Operation(
                    ContentProviderOperation.newUpdate(addressBook.syncAdapterURI(ContentUris.withAppendedId(RawContacts.CONTENT_URI, member)))
                            .withValue(RawContacts.DIRTY, 1)
                            .withYieldAllowed(true)
            ))

        batch.commit()
    }


    // helpers

    /**
     * Lists all members of this group.
     * @return list of all members' raw contact IDs
     * @throws ContactsStorageException on contact provider errorst
     */
    @Throws(ContactsStorageException::class)
    internal fun getMembers(): List<Long> {
        val id = requireNotNull(id)
        val members = LinkedList<Long>()
        try {
            addressBook.provider!!.query(
                    addressBook.syncAdapterURI(ContactsContract.Data.CONTENT_URI),
                    arrayOf(Data.RAW_CONTACT_ID),
                    "${GroupMembership.MIMETYPE}=? AND ${GroupMembership.GROUP_ROW_ID}=?",
                    arrayOf(GroupMembership.CONTENT_ITEM_TYPE, id.toString()),
                    null
            )?.use { cursor ->
                while (cursor.moveToNext())
                    members.add(cursor.getLong(0))
            }
        } catch(e: RemoteException) {
            throw ContactsStorageException("Couldn't list group members", e)
        }
        return members
    }


    // factory

    object Factory: AndroidGroupFactory<LocalGroup> {

        override fun newInstance(addressBook: AndroidAddressBook<out AndroidContact, LocalGroup>, id: Long, fileName: String?, eTag: String?) =
                LocalGroup(addressBook, id, fileName, eTag)

        override fun newInstance(addressBook: AndroidAddressBook<out AndroidContact, LocalGroup>, contact: Contact, fileName: String?, eTag: String?) =
                LocalGroup(addressBook, contact, fileName, eTag)

    }

}
