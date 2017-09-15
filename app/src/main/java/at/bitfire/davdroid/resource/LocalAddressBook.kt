/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.resource

import android.accounts.Account
import android.accounts.AccountManager
import android.content.*
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import android.util.Base64
import at.bitfire.davdroid.DavUtils
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.CollectionInfo
import at.bitfire.vcard4android.*
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.util.*
import java.util.logging.Level

class LocalAddressBook(
        private val context: Context,
        account: Account,
        provider: ContentProviderClient?
): AndroidAddressBook<LocalContact, LocalGroup>(account, provider, LocalContact.Factory, LocalGroup.Factory), LocalCollection<LocalResource> {

    companion object {

        val USER_DATA_MAIN_ACCOUNT_TYPE = "real_account_type"
        val USER_DATA_MAIN_ACCOUNT_NAME = "real_account_name"
        val USER_DATA_URL = "url"
        val USER_DATA_CTAG = "ctag"

        @JvmStatic
        @Throws(ContactsStorageException::class)
        fun create(context: Context, provider: ContentProviderClient, mainAccount: Account, info: CollectionInfo): LocalAddressBook {
            val accountManager = AccountManager.get(context)

            val account = Account(accountName(mainAccount, info), context.getString(R.string.account_type_address_book))
            if (!accountManager.addAccountExplicitly(account, null, initialUserData(mainAccount, info.url)))
                throw ContactsStorageException("Couldn't create address book account")

            val addressBook = LocalAddressBook(context, account, provider)
            ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true)
            return addressBook
        }

        @JvmStatic
        @Throws(ContactsStorageException::class)
        fun find(context: Context, provider: ContentProviderClient, mainAccount: Account?): List<LocalAddressBook> {
            val accountManager = AccountManager.get(context)

            val result = LinkedList<LocalAddressBook>()
            accountManager.getAccountsByType(context.getString(R.string.account_type_address_book))
                    .map { LocalAddressBook(context, it, provider) }
                    .filter { mainAccount == null || it.getMainAccount() == mainAccount }
                    .forEach { result += it }

            return result
        }

        @JvmStatic
        fun accountName(mainAccount: Account, info: CollectionInfo): String {
            val baos = ByteArrayOutputStream()
            baos.write(info.url.hashCode())
            val hash = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP or Base64.NO_PADDING)

            val sb = StringBuilder(if (info.displayName.isNullOrEmpty()) DavUtils.lastSegmentOfUrl(info.url) else info.displayName)
            sb      .append(" (")
                    .append(mainAccount.name)
                    .append(" ")
                    .append(hash)
                    .append(")")
            return sb.toString()
        }

        @JvmStatic
        fun initialUserData(mainAccount: Account, url: String): Bundle {
            val bundle = Bundle(3)
            bundle.putString(USER_DATA_MAIN_ACCOUNT_NAME, mainAccount.name)
            bundle.putString(USER_DATA_MAIN_ACCOUNT_TYPE, mainAccount.type)
            bundle.putString(USER_DATA_URL, url)
            return bundle
        }

    }

    /**
     * Whether contact groups (LocalGroup resources) are included in query results for
     * {@link #getAll()}, {@link #getDeleted()}, {@link #getDirty()} and
     * {@link #getWithoutFileName()}.
     */
    var includeGroups = true


    /* operations on the collection (address book) itself */

    @Throws(ContactsStorageException::class)
    fun update(info: CollectionInfo) {
        val newAccountName = accountName(getMainAccount(), info)
        if (account.name != newAccountName && Build.VERSION.SDK_INT >= 21) {
            val accountManager = AccountManager.get(context)
            val future = accountManager.renameAccount(account, newAccountName, {
                try {
                    // update raw contacts to new account name
                    provider?.let { provider ->
                        val values = ContentValues(1)
                        values.put(RawContacts.ACCOUNT_NAME, newAccountName)
                        provider.update(syncAdapterURI(RawContacts.CONTENT_URI), values, "${RawContacts.ACCOUNT_NAME}=?", arrayOf(account.name))
                    }
                } catch(e: RemoteException) {
                    Logger.log.log(Level.WARNING, "Couldn't re-assign contacts to new account name", e)
                }
            }, null)
            account = future.result
        }

        // make sure it will still be synchronized when contacts are updated
        ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true)
    }

    @Throws(ContactsStorageException::class)
    fun delete() {
        val accountManager = AccountManager.get(context)
        try {
            if (Build.VERSION.SDK_INT >= 22)
                accountManager.removeAccount(account, null, null, null)
            else
                @Suppress("deprecation")
                accountManager.removeAccount(account, null, null)
        } catch(e: Exception) {
            throw ContactsStorageException("Couldn't remove address book", e)
        }
    }


    /* operations on members (contacts/groups) */

    @Throws(ContactsStorageException::class, FileNotFoundException::class)
    fun findContactByUID(uid: String): LocalContact {
        val contacts = queryContacts("${AndroidContact.COLUMN_UID}=?", arrayOf(uid))
        if (contacts.isEmpty())
            throw FileNotFoundException()
        return contacts.first()
    }

    @Throws(ContactsStorageException::class)
    override fun getAll(): List<LocalResource> {
        val all = LinkedList<LocalResource>()
        all.addAll(queryContacts(null, null))
        if (includeGroups)
            all.addAll(queryGroups(null, null))
        return all
    }

    /**
     * Returns an array of local contacts/groups which have been deleted locally. (DELETED != 0).
     */
    @Throws(ContactsStorageException::class)
    override fun getDeleted(): List<LocalResource> {
        val deleted = LinkedList<LocalResource>()
        deleted.addAll(getDeletedContacts())
        if (includeGroups)
            deleted.addAll(getDeletedGroups())
        return deleted
    }

    /**
     * Queries all contacts with DIRTY flag and checks whether their data checksum has changed, i.e.
     * if they're "really dirty" (= data has changed, not only metadata, which is not hashed).
     * The DIRTY flag is removed from contacts which are not "really dirty", i.e. from contacts
     * whose contact data checksum has not changed.
     * @return number of "really dirty" contacts
     */
    @Throws(ContactsStorageException::class)
    fun verifyDirty(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            throw IllegalStateException("verifyDirty() should not be called on Android != 7")

        var reallyDirty = 0
        for (contact in getDirtyContacts())
            try {
                val lastHash = contact.getLastHashCode()
                val currentHash = contact.dataHashCode()
                if (lastHash == currentHash) {
                    // hash is code still the same, contact is not "really dirty" (only metadata been have changed)
                    Logger.log.log(Level.FINE, "Contact data hash has not changed, resetting dirty flag", contact)
                    contact.resetDirty()
                } else {
                    Logger.log.log(Level.FINE, "Contact data has changed from hash $lastHash to $currentHash", contact)
                    reallyDirty++
                }
            } catch(e: FileNotFoundException) {
                throw ContactsStorageException("Couldn't calculate hash code", e)
            }

        if (includeGroups)
            reallyDirty += getDirtyGroups().size

        return reallyDirty
    }

    /**
     * Returns an array of local contacts/groups which have been changed locally (DIRTY != 0).
     */
    @Throws(ContactsStorageException::class)
    override fun getDirty(): List<LocalResource> {
        val dirty = LinkedList<LocalResource>()
        dirty.addAll(getDirtyContacts())
        if (includeGroups)
            dirty.addAll(getDirtyGroups())
        return dirty
    }

    /**
     * Returns an array of local contacts which don't have a file name yet.
     */
    @Throws(ContactsStorageException::class)
    override fun getWithoutFileName(): List<LocalResource> {
        val nameless = LinkedList<LocalResource>()
        nameless.addAll(queryContacts("${AndroidContact.COLUMN_FILENAME} IS NULL", null))
        if (includeGroups)
            nameless.addAll(queryGroups("${AndroidGroup.COLUMN_FILENAME} IS NULL", null))
        return nameless
    }

    @Throws(ContactsStorageException::class)
    fun getDeletedContacts() = queryContacts("${RawContacts.DELETED} != 0", null)

    @Throws(ContactsStorageException::class)
    fun getDirtyContacts() = queryContacts("${RawContacts.DIRTY} != 0", null)

    @Throws(ContactsStorageException::class)
    fun getDeletedGroups() = queryGroups("${Groups.DELETED} != 0", null)

    @Throws(ContactsStorageException::class)
    fun getDirtyGroups() = queryGroups("${Groups.DIRTY} != 0", null)

    @Throws(ContactsStorageException::class)
    fun getByGroupMembership(groupID: Long): List<LocalContact> {
        try {
            val ids = HashSet<Long>()

            provider!!.query(syncAdapterURI(ContactsContract.Data.CONTENT_URI),
                    arrayOf(RawContacts.Data.RAW_CONTACT_ID),
                    "(${GroupMembership.MIMETYPE}=? AND ${GroupMembership.GROUP_ROW_ID}=?) OR (${CachedGroupMembership.MIMETYPE}=? AND ${CachedGroupMembership.GROUP_ID}=?)",
                    arrayOf(GroupMembership.CONTENT_ITEM_TYPE, groupID.toString(), CachedGroupMembership.CONTENT_ITEM_TYPE, groupID.toString()),
                    null)?.use { cursor ->
                while (cursor.moveToNext())
                    ids += cursor.getLong(0)
            }

            return ids.map { id -> LocalContact(this, id, null, null) }
        } catch (e: RemoteException) {
            throw ContactsStorageException("Couldn't query contacts", e)
        }
    }


    /* special group operations */

    /**
     * Finds the first group with the given title. If there is no group with this
     * title, a new group is created.
     * @param title     title of the group to look for
     * @return          id of the group with given title
     * @throws ContactsStorageException on contact provider errors
     */
    @Throws(ContactsStorageException::class)
    fun findOrCreateGroup(title: String): Long {
        try {
            provider!!.query(syncAdapterURI(Groups.CONTENT_URI), arrayOf(Groups._ID),
                    "${Groups.TITLE}=?", arrayOf(title), null)?.use { cursor ->
                if (cursor.moveToNext())
                    return cursor.getLong(0)
            }

            val values = ContentValues(1)
            values.put(Groups.TITLE, title)
            val uri = provider.insert(syncAdapterURI(Groups.CONTENT_URI), values)
            return ContentUris.parseId(uri)
        } catch(e: RemoteException) {
            throw ContactsStorageException("Couldn't find local contact group", e)
        }
    }

    @Throws(ContactsStorageException::class)
    fun removeEmptyGroups() {
        // find groups without members
        /** should be done using {@link Groups.SUMMARY_COUNT}, but it's not implemented in Android yet */
        queryGroups(null, null).filter { it.getMembers().isEmpty() }.forEach { group ->
            Logger.log.log(Level.FINE, "Deleting group", group)
            group.delete()
        }
    }

    @Throws(ContactsStorageException::class)
    fun removeGroups() {
        try {
            provider!!.delete(syncAdapterURI(Groups.CONTENT_URI), null, null)
        } catch(e: RemoteException) {
            throw ContactsStorageException("Couldn't remove all groups", e)
        }
    }


    // SETTINGS

    @Throws(ContactsStorageException::class)
    fun getMainAccount(): Account {
        val accountManager = AccountManager.get(context)
        val name = accountManager.getUserData(account, USER_DATA_MAIN_ACCOUNT_NAME)
        val type = accountManager.getUserData(account, USER_DATA_MAIN_ACCOUNT_TYPE)
        if (name != null && type != null)
            return Account(name, type)
        else
            throw ContactsStorageException("Address book doesn't exist anymore")
    }

    fun setMainAccount(mainAccount: Account) {
        val accountManager = AccountManager.get(context)
        accountManager.setUserData(account, USER_DATA_MAIN_ACCOUNT_NAME, mainAccount.name)
        accountManager.setUserData(account, USER_DATA_MAIN_ACCOUNT_TYPE, mainAccount.type)
    }

    @Throws(ContactsStorageException::class)
    fun getURL(): String {
        val accountManager = AccountManager.get(context)
        return accountManager.getUserData(account, USER_DATA_URL) ?: throw ContactsStorageException("Address book has no URL")
    }

    fun setURL(url: String) {
        val accountManager = AccountManager.get(context)
        accountManager.setUserData(account, USER_DATA_URL, url)
    }

    override fun getCTag(): String? {
        val accountManager = AccountManager.get(context)
        return accountManager.getUserData(account, USER_DATA_CTAG)
    }

    override fun setCTag(cTag: String?) {
        val accountManager = AccountManager.get(context)
        accountManager.setUserData(account, USER_DATA_CTAG, cTag)
    }

}
