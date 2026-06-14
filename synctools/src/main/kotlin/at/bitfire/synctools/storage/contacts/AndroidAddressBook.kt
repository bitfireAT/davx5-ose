/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.contacts

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.contacts.AddressContract.asSyncAdapter
import at.bitfire.synctools.storage.contacts.AndroidAddressBook.Companion.USER_DATA_READ_ONLY
import at.bitfire.synctools.storage.toContentValues
import at.bitfire.synctools.util.setAndVerifyUserData
import at.bitfire.synctools.vcard.GroupMethod
import java.io.FileNotFoundException
import java.util.LinkedList

open class AndroidAddressBook(
    context: Context,
    var addressBookAccount: Account,
    val provider: ContentProviderClient
) {

    private val accountManager = AccountManager.get(context)

    val groupMethod: GroupMethod = GroupMethod.GROUP_VCARDS

    /**
     * Read-only flag for the address book itself.
     *
     * Setting this flag:
     *
     * - stores the new value in [USER_DATA_READ_ONLY] and
     * - sets the read-only flag for all contacts and groups in the address book in the content provider, which will
     * prevent non-sync-adapter apps from modifying them. However new entries can still be created, so the address book
     * is not really read-only.
     *
     * Reading this flag returns the stored value from [USER_DATA_READ_ONLY].
     */
    var readOnly: Boolean
        get() = accountManager.getUserData(addressBookAccount, USER_DATA_READ_ONLY) != null
        set(readOnly) {
            accountManager.setAndVerifyUserData(addressBookAccount, USER_DATA_READ_ONLY, if (readOnly) "1" else null)

            // update raw contacts
            val rawContactValues = contentValuesOf(RawContacts.RAW_CONTACT_IS_READ_ONLY to if (readOnly) 1 else 0)
            provider.update(rawContactsSyncUri(), rawContactValues, null, null)

            // update data rows
            val dataValues = contentValuesOf(ContactsContract.Data.IS_READ_ONLY to if (readOnly) 1 else 0)
            provider.update(ContactsContract.Data.CONTENT_URI.asSyncAdapter(), dataValues, null, null)

            // update group rows
            val groupValues = contentValuesOf(Groups.GROUP_IS_READ_ONLY to if (readOnly) 1 else 0)
            provider.update(groupsSyncUri(), groupValues, null, null)
        }

    var settings: ContentValues
        /**
         * Retrieves [ContactsContract.Settings] for the current address book.
         * @throws FileNotFoundException if the settings row couldn't be fetched.
         * @throws android.os.RemoteException on content provider errors
         */
        get() {
            provider.query(ContactsContract.Settings.CONTENT_URI.asSyncAdapter(addressBookAccount), null, null, null, null)?.use { cursor ->
                if (cursor.moveToNext())
                    return cursor.toContentValues()
            }
            throw FileNotFoundException()
        }

        /**
         * Updates [ContactsContract.Settings] by inserting the given values into
         * the current address book.
         * @param values settings to be updated
         * @throws android.os.RemoteException on content provider errors
         */
        set(values) {
            values.put(ContactsContract.Settings.ACCOUNT_NAME, addressBookAccount.name)
            values.put(ContactsContract.Settings.ACCOUNT_TYPE, addressBookAccount.type)
            provider.insert(ContactsContract.Settings.CONTENT_URI.asSyncAdapter(addressBookAccount), values)
        }

    var syncState: ByteArray?
        get() = ContactsContract.SyncState.get(provider, addressBookAccount)
        set(data) = ContactsContract.SyncState.set(provider, addressBookAccount, data)

    /**
     * Counts the number of contacts in the address book that match the given selection criteria.
     *
     * @param where An optional filter declaring which rows to return.
     * @param whereArgs Optional arguments for [where].
     * @return The number of contacts matching the selection criteria.
     */
    fun countContacts(where: String?, whereArgs: Array<String>?): Int {
        provider.query(
            rawContactsSyncUri(), arrayOf(RawContacts._ID),
            where, whereArgs, null)?.use { cursor ->
            return cursor.count
        }
        // If the query was invalid, an exception should have been thrown. So this should never be reached:
        return 0
    }

    fun queryContacts(where: String?, whereArgs: Array<String>?): List<AndroidContact> {
        val contacts = LinkedList<AndroidContact>()
        provider.query(
            rawContactsSyncUri(), null,
                where, whereArgs, null)?.use { cursor ->
            while (cursor.moveToNext())
                contacts += AndroidContact(this, cursor.toContentValues())
        }
        return contacts
    }

    fun queryGroups(where: String?, whereArgs: Array<String>?): List<AndroidGroup> {
        val groups = LinkedList<AndroidGroup>()
        provider.query(groupsSyncUri(), null, where, whereArgs, null)?.use { cursor ->
            while (cursor.moveToNext())
                groups += AndroidGroup(this, cursor.toContentValues())
        }
        return groups
    }


    @Throws(FileNotFoundException::class)
    fun findContactById(id: Long) =
            queryContacts("${RawContacts._ID}=?", arrayOf(id.toString())).firstOrNull() ?: throw FileNotFoundException()

    fun findContactByUid(uid: String) =
        queryContacts("${AddressContract.RawContactColumns.UID}=?", arrayOf(uid)).firstOrNull()

    @Throws(FileNotFoundException::class)
    fun findGroupById(id: Long) =
        queryGroups("${Groups._ID}=?", arrayOf(id.toString())).firstOrNull() ?: throw FileNotFoundException()

    fun findOrCreateGroup(title: String): Long {
        provider.query(
            Groups.CONTENT_URI.asSyncAdapter(addressBookAccount), arrayOf(Groups._ID),
            "${Groups.TITLE}=?", arrayOf(title), null
        )?.use { cursor ->
            if (cursor.moveToNext())
                return cursor.getLong(0)
        }

        val values = contentValuesOf(Groups.TITLE to title)
        val uri = provider.insert(Groups.CONTENT_URI.asSyncAdapter(addressBookAccount), values)
            ?: throw RemoteException("Couldn't create contact group")
        return ContentUris.parseId(uri)
    }


    // helpers

    fun rawContactsSyncUri() = RawContacts.CONTENT_URI.asSyncAdapter(addressBookAccount)
    fun groupsSyncUri() = Groups.CONTENT_URI.asSyncAdapter(addressBookAccount)


    companion object {

        /**
         * Indicates whether the address book is currently set to read-only (i.e. its contacts and groups have the read-only flag).
         *
         * User data of the address book account (Boolean).
         */
        const val USER_DATA_READ_ONLY = "read_only"

    }

}
