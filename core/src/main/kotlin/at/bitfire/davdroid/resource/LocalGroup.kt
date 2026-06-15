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
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.contacts.AddressContract.CachedGroupMembership
import at.bitfire.synctools.storage.contacts.AddressContract.GroupColumns
import at.bitfire.synctools.storage.contacts.AddressContract.asSyncAdapter
import at.bitfire.synctools.storage.contacts.AndroidGroup
import at.bitfire.synctools.storage.contacts.ContactsBatchOperation
import com.google.common.base.MoreObjects
import java.util.LinkedList
import java.util.Optional

class LocalGroup(
    val localAddressBook: LocalAddressBook,
    val androidGroup: AndroidGroup
) : LocalAddress {

    constructor(localAddressBook: LocalAddressBook, values: ContentValues)
            : this(localAddressBook, AndroidGroup(localAddressBook.ab, values))


    private val provider get() = androidGroup.addressBook.provider

    override val id: Long?
        get() = androidGroup.id

    override val fileName: String?
        get() = androidGroup.fileName

    override val eTag: String?
        get() = androidGroup.eTag

    override val flags: Int
        get() = androidGroup.flags

    override var scheduleTag: String?
        get() = null
        set(_) = throw NotImplementedError()


    override fun clearDirty(fileName: Optional<String>, eTag: String?, scheduleTag: String?) {
        if (scheduleTag != null)
            throw IllegalArgumentException("Contact groups must not have a Schedule-Tag")
        val id = requireNotNull(id)

        val values = ContentValues(3)
        if (fileName.isPresent)
            values.put(GroupColumns.FILENAME, fileName.get())
        values.putNull(GroupColumns.ETAG)     // don't save changed ETag but null, so that the group is downloaded again, so that pendingMembers is updated
        values.put(Groups.DIRTY, 0)
        androidGroup.update(values)

        if (fileName.isPresent)
            androidGroup.fileName = fileName.get()
        androidGroup.eTag = null

        // update cached group memberships
        val batch = ContactsBatchOperation(provider)

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
        val batch = ContactsBatchOperation(provider)

        for (member in getMembers())
            batch += BatchOperation.CpoBuilder
                .newUpdate(ContentUris.withAppendedId(RawContacts.CONTENT_URI, member).asSyncAdapter())
                .withValue(RawContacts.DIRTY, 1)

        batch.commit()
    }

    override fun update(data: Contact, fileName: String?, eTag: String?, scheduleTag: String?, flags: Int) {
        androidGroup.fileName = fileName
        androidGroup.eTag = eTag

        // processes androidGroup.{fileName, eTag} and resets DIRTY flag
        androidGroup.update(data)
    }

    override fun updateFlags(flags: Int) {
        val values = contentValuesOf(GroupColumns.FLAGS to flags)
        provider.update(androidGroup.groupSyncURI(), values, null, null)
        androidGroup.flags = flags
    }

    override fun updateSequence(sequence: Int) = throw NotImplementedError()

    override fun updateUid(uid: String) {
        val values = contentValuesOf(GroupColumns.UID to uid)
        provider.update(androidGroup.groupSyncURI(), values, null, null)
    }

    override fun deleteLocal() {
        androidGroup.delete()
    }

    override fun resetDeleted() {
        val values = contentValuesOf(Groups.DELETED to 0)
        provider.update(androidGroup.groupSyncURI(), values, null, null)
    }

    override fun getDebugSummary() =
        MoreObjects.toStringHelper(this)
            .add("id", id)
            .add("fileName", fileName)
            .add("eTag", eTag)
            .add("flags", flags)
            .add(
                "contact",
                try {
                    androidGroup.getContact().toString()
                } catch (e: Exception) {
                    e
                }
            ).toString()

    override fun getViewUri(context: Context): Uri? = null


    // helpers

    /**
     * Lists all members of this group.
     * @return list of all members' raw contact IDs
     * @throws RemoteException on contact provider errors
     */
    internal fun getMembers(): List<Long> {
        val id = requireNotNull(id)
        val members = LinkedList<Long>()
        provider.query(
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

}
