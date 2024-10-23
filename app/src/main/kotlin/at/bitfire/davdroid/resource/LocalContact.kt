/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.content.ContentValues
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.RawContacts.Data
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.resource.contactrow.CachedGroupMembershipHandler
import at.bitfire.davdroid.resource.contactrow.GroupMembershipBuilder
import at.bitfire.davdroid.resource.contactrow.GroupMembershipHandler
import at.bitfire.davdroid.resource.contactrow.UnknownPropertiesBuilder
import at.bitfire.davdroid.resource.contactrow.UnknownPropertiesHandler
import at.bitfire.vcard4android.AndroidAddressBook
import at.bitfire.vcard4android.AndroidContact
import at.bitfire.vcard4android.AndroidContactFactory
import at.bitfire.vcard4android.BatchOperation
import at.bitfire.vcard4android.CachedGroupMembership
import at.bitfire.vcard4android.Contact
import ezvcard.Ezvcard
import java.io.FileNotFoundException
import java.util.UUID

class LocalContact: AndroidContact, LocalAddress {

    companion object {

        init {
            Contact.productID = "+//IDN bitfire.at//DAVx5/${BuildConfig.VERSION_NAME} ez-vcard/" + Ezvcard.VERSION
        }

        const val COLUMN_FLAGS = ContactsContract.RawContacts.SYNC4
    }

    override val addressBook: LocalAddressBook
        get() = super.addressBook as LocalAddressBook

    internal val cachedGroupMemberships = HashSet<Long>()
    internal val groupMemberships = HashSet<Long>()

    override var scheduleTag: String?
        get() = null
        set(_) = throw NotImplementedError()

    override var flags: Int = 0


    constructor(addressBook: LocalAddressBook, values: ContentValues): super(addressBook, values) {
        flags = values.getAsInteger(COLUMN_FLAGS) ?: 0
    }

    constructor(addressBook: LocalAddressBook, contact: Contact, fileName: String?, eTag: String?, _flags: Int): super(addressBook, contact, fileName, eTag) {
        flags = _flags
    }

    init {
        processor.registerHandler(CachedGroupMembershipHandler(this))
        processor.registerHandler(GroupMembershipHandler(this))
        processor.registerHandler(UnknownPropertiesHandler)
        processor.registerBuilderFactory(GroupMembershipBuilder.Factory(addressBook))
        processor.registerBuilderFactory(UnknownPropertiesBuilder.Factory)
    }


    override fun prepareForUpload(): String {
        val contact = getContact()
        val uid: String = contact.uid ?: run {
            // generate new UID
            val newUid = UUID.randomUUID().toString()

            // update in contacts provider
            val values = ContentValues(1)
            values.put(COLUMN_UID, newUid)
            addressBook.provider!!.update(rawContactSyncURI(), values, null, null)

            // update this event
            contact.uid = newUid

            newUid
        }

        return "$uid.vcf"
    }

    override fun clearDirty(fileName: String?, eTag: String?, scheduleTag: String?) {
        if (scheduleTag != null)
            throw IllegalArgumentException("Contacts must not have a Schedule-Tag")

        val values = ContentValues(4)
        if (fileName != null)
            values.put(COLUMN_FILENAME, fileName)
        values.put(COLUMN_ETAG, eTag)
        values.put(ContactsContract.RawContacts.DIRTY, 0)

        addressBook.provider!!.update(rawContactSyncURI(), values, null, null)

        if (fileName != null)
            this.fileName = fileName
        this.eTag = eTag
    }

    override fun resetDeleted() {
        val values = ContentValues(1)
        values.put(ContactsContract.Groups.DELETED, 0)
        addressBook.provider!!.update(rawContactSyncURI(), values, null, null)
    }

    fun resetDirty() {
        val values = ContentValues(1)
        values.put(ContactsContract.RawContacts.DIRTY, 0)
        addressBook.provider!!.update(rawContactSyncURI(), values, null, null)
    }

    override fun updateFlags(flags: Int) {
        val values = ContentValues(1)
        values.put(COLUMN_FLAGS, flags)
        addressBook.provider!!.update(rawContactSyncURI(), values, null, null)

        this.flags = flags
    }

    fun addToGroup(batch: BatchOperation, groupID: Long) {
        batch.enqueue(BatchOperation.CpoBuilder
                .newInsert(dataSyncURI())
                .withValue(GroupMembership.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE)
                .withValue(GroupMembership.RAW_CONTACT_ID, id)
                .withValue(GroupMembership.GROUP_ROW_ID, groupID))
        groupMemberships += groupID

        batch.enqueue(BatchOperation.CpoBuilder
                .newInsert(dataSyncURI())
                .withValue(CachedGroupMembership.MIMETYPE, CachedGroupMembership.CONTENT_ITEM_TYPE)
                .withValue(CachedGroupMembership.RAW_CONTACT_ID, id)
                .withValue(CachedGroupMembership.GROUP_ID, groupID)
        )
        cachedGroupMemberships += groupID
    }

    fun removeGroupMemberships(batch: BatchOperation) {
        batch.enqueue(BatchOperation.CpoBuilder
                .newDelete(dataSyncURI())
                .withSelection(
                        "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE} IN (?,?)",
                        arrayOf(id.toString(), GroupMembership.CONTENT_ITEM_TYPE, CachedGroupMembership.CONTENT_ITEM_TYPE)
                ))
        groupMemberships.clear()
        cachedGroupMemberships.clear()
    }

    /**
     * Returns the IDs of all groups the contact was member of (cached memberships).
     * Cached memberships are kept in sync with memberships by DAVx5 and are used to determine
     * whether a membership has been deleted/added when a raw contact is dirty.
     * @return set of {@link GroupMembership#GROUP_ROW_ID} (may be empty)
     * @throws FileNotFoundException if the current contact can't be found
     * @throws RemoteException on contacts provider errors
     */
    fun getCachedGroupMemberships(): Set<Long> {
        getContact()
        return cachedGroupMemberships
    }

    /**
     * Returns the IDs of all groups the contact is member of.
     * @return set of {@link GroupMembership#GROUP_ROW_ID}s (may be empty)
     * @throws FileNotFoundException if the current contact can't be found
     * @throws RemoteException on contacts provider errors
     */
    fun getGroupMemberships(): Set<Long> {
        getContact()
        return groupMemberships
    }


    // data rows
    override fun buildContact(builder: BatchOperation.CpoBuilder, update: Boolean) {
        builder.withValue(COLUMN_FLAGS, flags)
        super.buildContact(builder, update)
    }

    // factory

    object Factory: AndroidContactFactory<LocalContact> {
        override fun fromProvider(addressBook: AndroidAddressBook<LocalContact, *>, values: ContentValues) =
                LocalContact(addressBook as LocalAddressBook, values)
    }

}
