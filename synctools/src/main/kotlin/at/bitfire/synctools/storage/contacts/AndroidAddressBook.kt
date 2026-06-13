/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.storage.contacts

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.contacts.ContactContract.asSyncAdapter
import at.bitfire.synctools.storage.toContentValues
import at.bitfire.synctools.vcard.GroupMethod
import java.io.FileNotFoundException
import java.util.LinkedList

open class AndroidAddressBook<T1: AndroidContact, T2: AndroidGroup>(
    var addressBookAccount: Account,
    val provider: ContentProviderClient?,
    protected val contactFactory: AndroidContactFactory<T1>,
    protected val groupFactory: AndroidGroupFactory<T2>
) {

    open var readOnly: Boolean = false
    open val groupMethod: GroupMethod = GroupMethod.GROUP_VCARDS

    var settings: ContentValues
        /**
         * Retrieves [ContactsContract.Settings] for the current address book.
         * @throws FileNotFoundException if the settings row couldn't be fetched.
         * @throws android.os.RemoteException on content provider errors
         */
        get() {
            provider!!.query(ContactsContract.Settings.CONTENT_URI.asSyncAdapter(addressBookAccount), null, null, null, null)?.use { cursor ->
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
            provider!!.insert(ContactsContract.Settings.CONTENT_URI.asSyncAdapter(addressBookAccount), values)
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
        provider!!.query(rawContactsSyncUri(), arrayOf(RawContacts._ID),
            where, whereArgs, null)?.use { cursor ->
            return cursor.count
        }
        // If the query was invalid, an exception should have been thrown. So this should never be reached:
        return 0
    }

    fun queryContacts(where: String?, whereArgs: Array<String>?): List<T1> {
        val contacts = LinkedList<T1>()
        provider!!.query(rawContactsSyncUri(), null,
                where, whereArgs, null)?.use { cursor ->
            while (cursor.moveToNext())
                contacts += contactFactory.fromProvider(this, cursor.toContentValues())
        }
        return contacts
    }

    fun queryGroups(where: String?, whereArgs: Array<String>?, callback: (T2) -> Unit) {
        provider!!.query(groupsSyncUri(), null,
            where, whereArgs, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val group = groupFactory.fromProvider(this, cursor.toContentValues())
                callback(group)
            }
        }
    }

    fun queryGroups(where: String?, whereArgs: Array<String>?): List<T2> {
        val groups = LinkedList<T2>()
        queryGroups(where, whereArgs) { group ->
            groups += group
        }
        return groups
    }


    fun allGroups(callback: (T2) -> Unit) {
        queryGroups("${Groups.ACCOUNT_TYPE}=? AND ${Groups.ACCOUNT_NAME}=?", arrayOf(addressBookAccount.type, addressBookAccount.name)) { group ->
            callback(group)
        }
    }

    @Throws(FileNotFoundException::class)
    fun findContactById(id: Long) =
            queryContacts("${RawContacts._ID}=?", arrayOf(id.toString())).firstOrNull() ?: throw FileNotFoundException()

    fun findContactByUid(uid: String) =
            queryContacts("${AndroidContact.COLUMN_UID}=?", arrayOf(uid)).firstOrNull()

    @Throws(FileNotFoundException::class)
    fun findGroupById(id: Long) =
        queryGroups("${Groups._ID}=?", arrayOf(id.toString())).firstOrNull() ?: throw FileNotFoundException()

    fun findOrCreateGroup(title: String): Long {
        provider!!.query(
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

}
