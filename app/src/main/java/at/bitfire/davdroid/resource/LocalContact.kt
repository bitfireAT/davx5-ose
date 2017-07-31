/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.os.Build
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.RawContacts.Data
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.Logger
import at.bitfire.davdroid.model.UnknownProperties
import at.bitfire.vcard4android.*
import ezvcard.Ezvcard
import java.io.FileNotFoundException
import java.util.*

class LocalContact: AndroidContact, LocalResource {

    companion object {

        init {
            Contact.productID = "+//IDN bitfire.at//DAVdroid/" + BuildConfig.VERSION_NAME + " ez-vcard/" + Ezvcard.VERSION
        }

        val COLUMN_HASHCODE = ContactsContract.RawContacts.SYNC3

    }

    private val cachedGroupMemberships = HashSet<Long>()
    private val groupMemberships = HashSet<Long>()


    constructor(addressBook: AndroidAddressBook<LocalContact,*>, id: Long, fileName: String?, eTag: String?):
            super(addressBook, id, fileName, eTag)

    constructor(addressBook: AndroidAddressBook<LocalContact,*>, contact: Contact, fileName: String?, eTag: String?):
            super(addressBook, contact, fileName, eTag)


    @Throws(ContactsStorageException::class)
    fun resetDirty() {
        val values = ContentValues(1)
        values.put(ContactsContract.RawContacts.DIRTY, 0)
        try {
            addressBook.provider!!.update(rawContactSyncURI(), values, null, null)
        } catch(e: RemoteException) {
            throw ContactsStorageException("Couldn't clear dirty flag", e)
        }
    }

    @Throws(ContactsStorageException::class)
    override fun clearDirty(eTag: String?) {
        try {
            val values = ContentValues(3)
            values.put(COLUMN_ETAG, eTag)
            values.put(ContactsContract.RawContacts.DIRTY, 0)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                // workaround for Android 7 which sets DIRTY flag when only meta-data is changed
                val hashCode = dataHashCode()
                values.put(COLUMN_HASHCODE, hashCode)
                Logger.log.finer("Clearing dirty flag with eTag = $eTag, contact hash = $hashCode")
            }

            addressBook.provider!!.update(rawContactSyncURI(), values, null, null)

            this.eTag = eTag
        } catch(e: Exception) {
            throw ContactsStorageException("Couldn't clear dirty flag", e)
        }
    }

    @Throws(ContactsStorageException::class)
    override fun prepareForUpload() {
        try {
            val uid = UUID.randomUUID().toString()
            val newFileName = uid + ".vcf"

            val values = ContentValues(2)
            values.put(COLUMN_FILENAME, newFileName)
            values.put(COLUMN_UID, uid)
            addressBook.provider!!.update(rawContactSyncURI(), values, null, null)

            fileName = newFileName
        } catch(e: RemoteException) {
            throw ContactsStorageException("Couldn't update UID", e)
        }
    }


    @Throws(ContactsStorageException::class)
    override fun populateData(mimeType: String, row: ContentValues) {
        when (mimeType) {
            CachedGroupMembership.CONTENT_ITEM_TYPE ->
                cachedGroupMemberships.add(row.getAsLong(CachedGroupMembership.GROUP_ID))
            GroupMembership.CONTENT_ITEM_TYPE ->
                groupMemberships.add(row.getAsLong(GroupMembership.GROUP_ROW_ID))
            UnknownProperties.CONTENT_ITEM_TYPE ->
                try {
                    contact!!.unknownProperties = row.getAsString(UnknownProperties.UNKNOWN_PROPERTIES)
                } catch(e: FileNotFoundException) {
                    throw ContactsStorageException("Couldn't fetch data rows", e)
                }
        }
    }

    @Throws(ContactsStorageException::class)
    override fun insertDataRows(batch: BatchOperation) {
        super.insertDataRows(batch)

        try {
            contact!!.unknownProperties?.let { unknownProperties ->
                val op: BatchOperation.Operation
                val builder = ContentProviderOperation.newInsert(dataSyncURI())
                if (id == null)
                    op = BatchOperation.Operation(builder, UnknownProperties.RAW_CONTACT_ID, 0)
                else {
                    op = BatchOperation.Operation(builder)
                    builder.withValue(UnknownProperties.RAW_CONTACT_ID, id)
                }
                builder .withValue(UnknownProperties.MIMETYPE, UnknownProperties.CONTENT_ITEM_TYPE)
                        .withValue(UnknownProperties.UNKNOWN_PROPERTIES, unknownProperties)
                batch.enqueue(op)
            }
        } catch(e: FileNotFoundException) {
            throw ContactsStorageException("Couldn't insert data rows", e)
        }

    }


    /**
     * Calculates a hash code from the contact's data (VCard) and group memberships.
     * Attention: re-reads {@link #contact} from the database, discarding all changes in memory
     * @return hash code of contact data (including group memberships)
     */
    @Throws(FileNotFoundException::class, ContactsStorageException::class)
    internal fun dataHashCode(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            throw IllegalStateException("dataHashCode() should not be called on Android != 7")

        // reset contact so that getContact() reads from database
        contact = null

        // groupMemberships is filled by getContact()
        val dataHash = contact!!.hashCode()
        val groupHash = groupMemberships.hashCode()
        Logger.log.finest("Calculated data hash = $dataHash, group memberships hash = $groupHash")
        return dataHash xor groupHash
    }

    @Throws(ContactsStorageException::class)
    fun updateHashCode(batch: BatchOperation?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            throw IllegalStateException("updateHashCode() should not be called on Android != 7")

        val values = ContentValues(1)
        try {
            val hashCode = dataHashCode()
            Logger.log.fine("Storing contact hash = $hashCode")
            values.put(COLUMN_HASHCODE, hashCode)

            if (batch == null)
                addressBook.provider!!.update(rawContactSyncURI(), values, null, null)
            else {
                val builder = ContentProviderOperation
                        .newUpdate(rawContactSyncURI())
                        .withValues(values)
                batch.enqueue(BatchOperation.Operation(builder))
            }
        } catch(e: Exception) {
            throw ContactsStorageException("Couldn't store contact checksum", e)
        }
    }

    @Throws(ContactsStorageException::class)
    fun getLastHashCode(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            throw IllegalStateException("getLastHashCode() should not be called on Android != 7")

        try {
            addressBook.provider!!.query(rawContactSyncURI(), arrayOf(COLUMN_HASHCODE), null, null, null)?.use { c ->
                if (c.moveToNext() && !c.isNull(0))
                    return c.getInt(0)
            }
            return 0
        } catch(e: RemoteException) {
            throw ContactsStorageException("Could't read last hash code", e)
        }
    }


    fun addToGroup(batch: BatchOperation, groupID: Long) {
        batch.enqueue(BatchOperation.Operation(
                ContentProviderOperation.newInsert(dataSyncURI())
                        .withValue(GroupMembership.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE)
                        .withValue(GroupMembership.RAW_CONTACT_ID, id)
                        .withValue(GroupMembership.GROUP_ROW_ID, groupID)
        ))
        groupMemberships.add(groupID)

        batch.enqueue(BatchOperation.Operation(
                ContentProviderOperation.newInsert(dataSyncURI())
                        .withValue(CachedGroupMembership.MIMETYPE, CachedGroupMembership.CONTENT_ITEM_TYPE)
                        .withValue(CachedGroupMembership.RAW_CONTACT_ID, id)
                        .withValue(CachedGroupMembership.GROUP_ID, groupID)
                        .withYieldAllowed(true)
        ))
        cachedGroupMemberships.add(groupID)
    }

    fun removeGroupMemberships(batch: BatchOperation) {
        batch.enqueue(BatchOperation.Operation(
                ContentProviderOperation.newDelete(dataSyncURI())
                        .withSelection(
                                Data.RAW_CONTACT_ID + "=? AND " + Data.MIMETYPE + " IN (?,?)",
                                arrayOf(id.toString(), GroupMembership.CONTENT_ITEM_TYPE, CachedGroupMembership.CONTENT_ITEM_TYPE)
                        )
                        .withYieldAllowed(true)
        ))
        groupMemberships.clear()
        cachedGroupMemberships.clear()
    }

    /**
     * Returns the IDs of all groups the contact was member of (cached memberships).
     * Cached memberships are kept in sync with memberships by DAVdroid and are used to determine
     * whether a membership has been deleted/added when a raw contact is dirty.
     * @return set of {@link GroupMembership#GROUP_ROW_ID} (may be empty)
     * @throws ContactsStorageException   on contact provider errors
     * @throws FileNotFoundException      if the current contact can't be found
     */
    @Throws(FileNotFoundException::class, ContactsStorageException::class)
    fun getCachedGroupMemberships(): Set<Long> {
        contact
        return cachedGroupMemberships
    }

    /**
     * Returns the IDs of all groups the contact is member of.
     * @return set of {@link GroupMembership#GROUP_ROW_ID}s (may be empty)
     * @throws ContactsStorageException   on contact provider errors
     * @throws FileNotFoundException      if the current contact can't be found
     */
    @Throws(FileNotFoundException::class, ContactsStorageException::class)
    fun getGroupMemberships(): Set<Long> {
        contact
        return groupMemberships
    }


    // factory

    object Factory: AndroidContactFactory<LocalContact> {

        override fun newInstance(addressBook: AndroidAddressBook<LocalContact, *>, id: Long, fileName: String?, eTag: String?) =
                LocalContact(addressBook, id, fileName, eTag)

        override fun newInstance(addressBook: AndroidAddressBook<LocalContact, *>, contact: Contact, fileName: String?, eTag: String?) =
                LocalContact(addressBook, contact, fileName, eTag)

    }

}
