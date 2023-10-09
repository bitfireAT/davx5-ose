/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.resource

import android.content.ContentValues
import android.os.Build
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.RawContacts.Data
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.contactrow.*
import at.bitfire.vcard4android.*
import ezvcard.Ezvcard
import org.apache.commons.lang3.StringUtils
import java.io.FileNotFoundException
import java.util.*

class LocalContact: AndroidContact, LocalAddress {

    override val addressBook: LocalAddressBook
        get() = super.addressBook as LocalAddressBook

    companion object {
        init {
            Contact.productID = "+//IDN bitfire.at//${BuildConfig.userAgent}/${BuildConfig.VERSION_NAME} ez-vcard/" + Ezvcard.VERSION
        }

        const val COLUMN_FLAGS = ContactsContract.RawContacts.SYNC4
        const val COLUMN_HASHCODE = ContactsContract.RawContacts.SYNC3
    }

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
        var uid: String? = null
        addressBook.provider!!.query(rawContactSyncURI(), arrayOf(COLUMN_UID), null, null, null)?.use { cursor ->
            if (cursor.moveToNext())
                uid = StringUtils.trimToNull(cursor.getString(0))
        }

        if (uid == null) {
            // generate new UID
            uid = UUID.randomUUID().toString()

            val values = ContentValues(1)
            values.put(COLUMN_UID, uid)
            addressBook.provider!!.update(rawContactSyncURI(), values, null, null)

            getContact().uid = uid
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // workaround for Android 7 which sets DIRTY flag when only meta-data is changed
            val hashCode = dataHashCode()
            values.put(COLUMN_HASHCODE, hashCode)
            Logger.log.finer("Clearing dirty flag with eTag = $eTag, contact hash = $hashCode")
        }

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


    /**
     * Calculates a hash code from the contact's data (VCard) and group memberships.
     * Attention: re-reads {@link #contact} from the database, discarding all changes in memory
     * @return hash code of contact data (including group memberships)
     */
    internal fun dataHashCode(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            throw IllegalStateException("dataHashCode() should not be called on Android != 7")

        // reset contact so that getContact() reads from database
        _contact = null

        // groupMemberships is filled by getContact()
        val dataHash = getContact().hashCode()
        val groupHash = groupMemberships.hashCode()
        Logger.log.finest("Calculated data hash = $dataHash, group memberships hash = $groupHash")
        return dataHash xor groupHash
    }

    fun updateHashCode(batch: BatchOperation?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            throw IllegalStateException("updateHashCode() should not be called on Android != 7")

        val hashCode = dataHashCode()
        Logger.log.fine("Storing contact hash = $hashCode")

        if (batch == null) {
            val values = ContentValues(1)
            values.put(COLUMN_HASHCODE, hashCode)
            addressBook.provider!!.update(rawContactSyncURI(), values, null, null)
        } else
            batch.enqueue(BatchOperation.CpoBuilder
                    .newUpdate(rawContactSyncURI())
                    .withValue(COLUMN_HASHCODE, hashCode))
    }

    fun getLastHashCode(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            throw IllegalStateException("getLastHashCode() should not be called on Android != 7")

        addressBook.provider!!.query(rawContactSyncURI(), arrayOf(COLUMN_HASHCODE), null, null, null)?.use { c ->
            if (c.moveToNext() && !c.isNull(0))
                return c.getInt(0)
        }
        return 0
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
