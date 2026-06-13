/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.contacts

import android.content.ContentUris
import android.content.ContentValues
import android.content.EntityIterator
import android.database.DatabaseUtils
import android.net.Uri
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.RawContacts
import android.provider.ContactsContract.RawContacts.Data
import androidx.annotation.CallSuper
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.mapping.contacts.RawContactBuilder
import at.bitfire.synctools.mapping.contacts.RawContactHandler
import at.bitfire.synctools.mapping.contacts.builder.PhotoBuilder
import at.bitfire.synctools.storage.BatchOperation
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.contacts.AddressContract.CachedGroupMembership
import at.bitfire.synctools.storage.contacts.AddressContract.asSyncAdapter
import java.io.FileNotFoundException

open class AndroidContact(
    open val addressBook: AndroidAddressBook<out AndroidContact, out AndroidGroup>
) {

    companion object {

        const val COLUMN_FILENAME = RawContacts.SOURCE_ID
        const val COLUMN_UID = RawContacts.SYNC1
        const val COLUMN_ETAG = RawContacts.SYNC2

    }

    var id: Long? = null
        protected set

    var fileName: String? = null
        protected set

    var eTag: String? = null

    /**
     * IDs of groups this contact's cached group membership rows belong to.
     * Only filled after [getContact] has been called.
     *
     * Used to detect which groups have become dirty when a contact's memberships change.
     * See [AddressContract.CachedGroupMembership] for details.
     */
    val cachedGroupMemberships = HashSet<Long>()

    /**
     * IDs of groups this contact is currently a member of.
     * Only filled after [getContact] has been called.
     */
    val groupMemberships = HashSet<Long>()

    private val rawContactHandler: RawContactHandler by lazy {
        RawContactHandler(this)
    }

    private val rawContactBuilder: RawContactBuilder by lazy {
        RawContactBuilder(addressBook)
    }


    /**
     * Creates a new instance, initialized with some metadata. Usually used to insert a contact to an address book.
     */
    constructor(addressBook: AndroidAddressBook<out AndroidContact, out AndroidGroup>, _contact: Contact, _fileName: String?, _eTag: String?): this(addressBook) {
        fileName = _fileName
        eTag = _eTag
        setContact(_contact)
    }

    /**
     * Creates a new instance, initialized with metadata from the content provider. Usually used when reading a contact from an address book.
     */
    constructor(addressBook: AndroidAddressBook<out AndroidContact, out AndroidGroup>, values: ContentValues): this(addressBook) {
        id = values.getAsLong(RawContacts._ID)
        fileName = values.getAsString(COLUMN_FILENAME)
        eTag = values.getAsString(COLUMN_ETAG)
    }


    /**
     * Cached copy of the [Contact]. If this is null, [getContact] must generate the [Contact]
     * from the database and then set this property.
     */
    private var cachedContact: Contact? = null

    /**
     * Fetches contact data from the contacts provider.
     *
     * @throws IllegalArgumentException if there's no [id] (usually because the contact has never been saved yet)
     * @throws FileNotFoundException when the contact is not available (anymore)
     * @throws RemoteException on contact provider errors
     */
    fun getContact(): Contact {
        // use cached version if available
        cachedContact?.let { return it }

        val id = requireNotNull(id)
        var iter: EntityIterator? = null
        try {
            iter = RawContacts.newEntityIterator(addressBook.provider!!.query(
                ContactsContract.RawContactsEntity.CONTENT_URI.asSyncAdapter(),
                    null, RawContacts._ID + "=?", arrayOf(id.toString()), null))

            if (iter.hasNext()) {
                val contact = Contact()

                // process raw contact itself
                val e = iter.next()
                rawContactHandler.handleRawContact(e.entityValues, contact)

                // process data rows of raw contact
                for (subValue in e.subValues)
                    rawContactHandler.handleDataRow(subValue.values, contact)

                cachedContact = contact
                return contact

            } else
                // no raw contact with this ID
                throw FileNotFoundException()

        } finally {
            iter?.close()
        }
    }

    fun setContact(newContact: Contact?) {
        cachedContact = newContact
    }


    fun add(): Uri {
        val provider = addressBook.provider!!
        val batch = ContactsBatchOperation(provider)

        val builder = BatchOperation.CpoBuilder.newInsert(RawContacts.CONTENT_URI.asSyncAdapter())
        buildContact(builder, false)
        batch += builder

        insertDataRows(batch)

        batch.commit()
        val resultUri = batch.getResult(0)?.uri
            ?: throw LocalStorageException("Empty result from content provider when adding contact")
        id = ContentUris.parseId(resultUri)

        getContact().photo?.let { photo ->
            PhotoBuilder.insertPhoto(provider, id!!, photo)
        }

        return resultUri
    }

    fun update(data: Contact): Uri {
        setContact(data)

        val provider = addressBook.provider!!
        val batch = ContactsBatchOperation(provider)
        val uri = rawContactSyncURI()
        val builder = BatchOperation.CpoBuilder.newUpdate(uri)
        buildContact(builder, true)
        batch += builder

        // Delete known data rows before adding the new ones.
        // - We don't delete group memberships because they're managed separately.
        // - We'll only delete rows we have inserted so that unknown rows like
        //   vnd.android.cursor.item/important_people (= contact is in Samsung "edge panel") remain untouched.
        val typesToRemove = rawContactBuilder.builderMimeTypes()
        val sqlTypesToRemove = typesToRemove.joinToString(",") { mimeType ->
            DatabaseUtils.sqlEscapeString(mimeType)
        }
        batch += BatchOperation.CpoBuilder
                .newDelete(dataSyncURI())
                .withSelection(Data.RAW_CONTACT_ID + "=? AND ${Data.MIMETYPE} IN ($sqlTypesToRemove)", arrayOf(id!!.toString()))

        insertDataRows(batch)
        batch.commit()

        getContact().photo?.let { photo ->
            PhotoBuilder.insertPhoto(provider, id!!, photo)
        }

        return uri
    }

    /**
     * Deletes an existing contact from the contacts provider.
     *
     * @return number of affected rows
     *
     * @throws RemoteException on contacts provider errors
     */
    fun delete() = addressBook.provider!!.delete(rawContactSyncURI(), null, null)


    @CallSuper
    protected open fun buildContact(builder: BatchOperation.CpoBuilder, update: Boolean) {
        if (!update)
            builder	.withValue(RawContacts.ACCOUNT_NAME, addressBook.addressBookAccount.name)
                    .withValue(RawContacts.ACCOUNT_TYPE, addressBook.addressBookAccount.type)

        builder .withValue(RawContacts.DIRTY, 0)
                .withValue(RawContacts.DELETED, 0)
                .withValue(COLUMN_FILENAME, fileName)
                .withValue(COLUMN_ETAG, eTag)
                .withValue(COLUMN_UID, getContact().uid)

        if (addressBook.readOnly)
            builder.withValue(RawContacts.RAW_CONTACT_IS_READ_ONLY, 1)
    }


    /**
     * Inserts the data rows for a given raw contact.
     *
     * @param  batch    batch operation used to insert the data rows
     *
     * @throws RemoteException on contact provider errors
     */
    protected fun insertDataRows(batch: ContactsBatchOperation) {
        val contact = getContact()
        rawContactBuilder.insertDataRows(dataSyncURI(), id, contact, batch, addressBook.readOnly)
    }


    // group membership management

    fun addToGroup(batch: ContactsBatchOperation, groupID: Long) {
        batch += BatchOperation.CpoBuilder
            .newInsert(dataSyncURI())
            .withValue(GroupMembership.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE)
            .withValue(GroupMembership.RAW_CONTACT_ID, id!!)
            .withValue(GroupMembership.GROUP_ROW_ID, groupID)
        groupMemberships += groupID

        batch += BatchOperation.CpoBuilder
            .newInsert(dataSyncURI())
            .withValue(CachedGroupMembership.MIMETYPE, CachedGroupMembership.CONTENT_ITEM_TYPE)
            .withValue(CachedGroupMembership.RAW_CONTACT_ID, id)
            .withValue(CachedGroupMembership.GROUP_ID, groupID)
        cachedGroupMemberships += groupID
    }

    fun removeGroupMemberships(batch: BatchOperation) {
        batch += BatchOperation.CpoBuilder
            .newDelete(dataSyncURI())
            .withSelection(
                "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE} IN (?,?)",
                arrayOf(id!!.toString(), GroupMembership.CONTENT_ITEM_TYPE, CachedGroupMembership.CONTENT_ITEM_TYPE)
            )
        groupMemberships.clear()
        cachedGroupMemberships.clear()
    }

    /**
     * Returns the IDs of all groups the contact was member of (cached memberships).
     * Cached memberships are kept in sync with memberships by DAVx5 and are used to determine
     * whether a membership has been deleted/added when a raw contact is dirty.
     * @return set of [GroupMembership.GROUP_ROW_ID] (may be empty)
     * @throws FileNotFoundException if the current contact can't be found
     * @throws RemoteException on contacts provider errors
     */
    fun getCachedGroupMemberships(): Set<Long> {
        getContact()
        return cachedGroupMemberships
    }

    /**
     * Returns the IDs of all groups the contact is member of.
     * @return set of [GroupMembership.GROUP_ROW_ID]s (may be empty)
     * @throws FileNotFoundException if the current contact can't be found
     * @throws RemoteException on contacts provider errors
     */
    fun getGroupMemberships(): Set<Long> {
        getContact()
        return groupMemberships
    }


    // helpers

    fun rawContactSyncURI(): Uri {
        val id = requireNotNull(id)
        return ContentUris.withAppendedId(RawContacts.CONTENT_URI, id).asSyncAdapter()
    }

    fun dataSyncURI() = ContactsContract.Data.CONTENT_URI.asSyncAdapter()

    override fun toString() =
        "AndroidContact(id=$id, fileName=$fileName, eTag=$eTag, cachedContact=$cachedContact)"

}