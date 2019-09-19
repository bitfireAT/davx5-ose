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
import at.bitfire.davdroid.model.Collection
import at.bitfire.davdroid.model.SyncState
import at.bitfire.vcard4android.*
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.logging.Level

/**
 * A local address book. Requires an own Android account, because Android manages contacts per
 * account and there is no such thing as "address books". So, DAVx5 creates a "DAVx5
 * address book" account for every CardDAV address book. These accounts are bound to a
 * DAVx5 main account.
 */
class LocalAddressBook(
        private val context: Context,
        account: Account,
        provider: ContentProviderClient?
): AndroidAddressBook<LocalContact, LocalGroup>(account, provider, LocalContact.Factory, LocalGroup.Factory), LocalCollection<LocalAddress> {

    companion object {

        const val USER_DATA_MAIN_ACCOUNT_TYPE = "real_account_type"
        const val USER_DATA_MAIN_ACCOUNT_NAME = "real_account_name"
        const val USER_DATA_URL = "url"
        const val USER_DATA_READ_ONLY = "read_only"

        fun create(context: Context, provider: ContentProviderClient, mainAccount: Account, info: Collection): LocalAddressBook {
            val accountManager = AccountManager.get(context)

            val account = Account(accountName(mainAccount, info), context.getString(R.string.account_type_address_book))
            val userData = initialUserData(mainAccount, info.url.toString())
            Logger.log.log(Level.INFO, "Creating local address book $account", userData)
            if (!accountManager.addAccountExplicitly(account, null, userData))
                throw IllegalStateException("Couldn't create address book account")

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
                // Android < 7 seems to lose the initial user data sometimes, so set it a second time
                // https://forums.bitfire.at/post/11644
                userData.keySet().forEach { key ->
                    accountManager.setUserData(account, key, userData.getString(key))
                }

            val addressBook = LocalAddressBook(context, account, provider)
            ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true)

            // initialize Contacts Provider Settings
            val values = ContentValues(2)
            values.put(ContactsContract.Settings.SHOULD_SYNC, 1)
            values.put(ContactsContract.Settings.UNGROUPED_VISIBLE, 1)
            addressBook.settings = values
            addressBook.readOnly = !info.privWriteContent || info.forceReadOnly

            return addressBook
        }

        fun findAll(context: Context, provider: ContentProviderClient?, mainAccount: Account?) = AccountManager.get(context)
                .getAccountsByType(context.getString(R.string.account_type_address_book))
                .map { LocalAddressBook(context, it, provider) }
                .filter { mainAccount == null || it.mainAccount == mainAccount }
                .toList()

        fun accountName(mainAccount: Account, info: Collection): String {
            val baos = ByteArrayOutputStream()
            baos.write(info.url.hashCode())
            val hash = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP or Base64.NO_PADDING)

            val sb = StringBuilder(info.displayName.let {
                if (it.isNullOrEmpty())
                    DavUtils.lastSegmentOfUrl(info.url)
                else
                    it
            })
            sb.append(" (${mainAccount.name} $hash)")
            return sb.toString()
        }

        fun initialUserData(mainAccount: Account, url: String): Bundle {
            val bundle = Bundle(3)
            bundle.putString(USER_DATA_MAIN_ACCOUNT_NAME, mainAccount.name)
            bundle.putString(USER_DATA_MAIN_ACCOUNT_TYPE, mainAccount.type)
            bundle.putString(USER_DATA_URL, url)
            return bundle
        }

        fun mainAccount(context: Context, account: Account): Account =
                if (account.type == context.getString(R.string.account_type_address_book)) {
                    val manager = AccountManager.get(context)
                    Account(
                            manager.getUserData(account, USER_DATA_MAIN_ACCOUNT_NAME),
                            manager.getUserData(account, USER_DATA_MAIN_ACCOUNT_TYPE)
                    )
                } else
                    account

    }

    override val title = account.name!!

    /**
     * Whether contact groups ([LocalGroup]) are included in query results
     * and are affected by updates/deletes on generic members.
     *
     * For instance, if this option is disabled, [findDirty] will find only dirty [LocalContact]s,
     * but if it is enabled, [findDirty] will find dirty [LocalContact]s and [LocalGroup]s.
     */
    var includeGroups = true

    private var _mainAccount: Account? = null
    var mainAccount: Account
        get() {
            _mainAccount?.let { return it }

            AccountManager.get(context).let { accountManager ->
                val name = accountManager.getUserData(account, USER_DATA_MAIN_ACCOUNT_NAME)
                val type = accountManager.getUserData(account, USER_DATA_MAIN_ACCOUNT_TYPE)
                if (name != null && type != null)
                    return Account(name, type)
                else
                    throw IllegalStateException("No main account assigned to address book account")
            }
        }
        set(newMainAccount) {
            AccountManager.get(context).let { accountManager ->
                accountManager.setUserData(account, USER_DATA_MAIN_ACCOUNT_NAME, newMainAccount.name)
                accountManager.setUserData(account, USER_DATA_MAIN_ACCOUNT_TYPE, newMainAccount.type)
            }

            _mainAccount = newMainAccount
        }

    var url: String
        get() = AccountManager.get(context).getUserData(account, USER_DATA_URL)
                ?: throw IllegalStateException("Address book has no URL")
        set(url) = AccountManager.get(context).setUserData(account, USER_DATA_URL, url)

    override var readOnly: Boolean
        get() = AccountManager.get(context).getUserData(account, USER_DATA_READ_ONLY) != null
        set(readOnly) = AccountManager.get(context).setUserData(account, USER_DATA_READ_ONLY, if (readOnly) "1" else null)

    override var lastSyncState: SyncState?
        get() = syncState?.let { SyncState.fromString(String(it)) }
        set(state) {
            syncState = state?.toString()?.toByteArray()
        }


    /* operations on the collection (address book) itself */

    override fun markNotDirty(flags: Int): Int {
        val values = ContentValues(1)
        values.put(LocalContact.COLUMN_FLAGS, flags)
        var number = provider!!.update(rawContactsSyncUri(), values, "${RawContacts.DIRTY}=0", null)

        if (includeGroups) {
            values.clear()
            values.put(LocalGroup.COLUMN_FLAGS, flags)
            number += provider.update(groupsSyncUri(), values, "${Groups.DIRTY}=0", null)
        }

        return number
    }

    override fun removeNotDirtyMarked(flags: Int): Int {
        var number = provider!!.delete(rawContactsSyncUri(),
                "${RawContacts.DIRTY}=0 AND ${LocalContact.COLUMN_FLAGS}=?", arrayOf(flags.toString()))

        if (includeGroups)
            number += provider.delete(groupsSyncUri(),
                    "${Groups.DIRTY}=0 AND ${LocalGroup.COLUMN_FLAGS}=?", arrayOf(flags.toString()))

        return number
    }

    fun update(info: Collection) {
        val newAccountName = accountName(mainAccount, info)

        if (account.name != newAccountName && Build.VERSION.SDK_INT >= 21) {
            // no need to re-assign contacts to new account, because they will be deleted by contacts provider in any case
            val accountManager = AccountManager.get(context)
            val future = accountManager.renameAccount(account, newAccountName, null, null)
            account = future.result
        }

        val nowReadOnly = !info.privWriteContent || info.forceReadOnly
        if (nowReadOnly != readOnly) {
            Constants.log.info("Address book now read-only = $nowReadOnly, updating contacts")

            // update address book itself
            readOnly = nowReadOnly

            // update raw contacts
            val rawContactValues = ContentValues(1)
            rawContactValues.put(RawContacts.RAW_CONTACT_IS_READ_ONLY, if (nowReadOnly) 1 else 0)
            provider!!.update(rawContactsSyncUri(), rawContactValues, null, null)

            // update data rows
            val dataValues = ContentValues(1)
            dataValues.put(ContactsContract.Data.IS_READ_ONLY, if (nowReadOnly) 1 else 0)
            provider.update(syncAdapterURI(ContactsContract.Data.CONTENT_URI), dataValues, null, null)
        }

        // make sure it will still be synchronized when contacts are updated
        ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true)
    }

    fun delete() {
        val accountManager = AccountManager.get(context)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= 22)
            accountManager.removeAccount(account, null, null, null)
        else
            accountManager.removeAccount(account, null, null)
    }


    /* operations on members (contacts/groups) */

    override fun findByName(name: String): LocalAddress? {
        val result = queryContacts("${AndroidContact.COLUMN_FILENAME}=?", arrayOf(name)).firstOrNull()
        return if (includeGroups)
            result ?: queryGroups("${AndroidGroup.COLUMN_FILENAME}=?", arrayOf(name)).firstOrNull()
        else
            result
    }


    /**
     * Returns an array of local contacts/groups which have been deleted locally. (DELETED != 0).
     * @throws RemoteException on content provider errors
     */
    override fun findDeleted() =
            if (includeGroups)
                findDeletedContacts() + findDeletedGroups()
            else
                findDeletedContacts()

    fun findDeletedContacts() = queryContacts("${RawContacts.DELETED}!=0", null)
    fun findDeletedGroups() = queryGroups("${Groups.DELETED}!=0", null)

    /**
     * Returns an array of local contacts/groups which have been changed locally (DIRTY != 0).
     * @throws RemoteException on content provider errors
     */
    override fun findDirty() =
            if (includeGroups)
                findDirtyContacts() + findDirtyGroups()
            else
                findDirtyContacts()

    fun findDirtyContacts() = queryContacts("${RawContacts.DIRTY}!=0", null)
    fun findDirtyGroups() = queryGroups("${Groups.DIRTY}!=0", null)


    /**
     * Queries all contacts with DIRTY flag and checks whether their data checksum has changed, i.e.
     * if they're "really dirty" (= data has changed, not only metadata, which is not hashed).
     * The DIRTY flag is removed from contacts which are not "really dirty", i.e. from contacts
     * whose contact data checksum has not changed.
     * @return number of "really dirty" contacts
     * @throws RemoteException on content provider errors
     */
    fun verifyDirty(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            throw IllegalStateException("verifyDirty() should not be called on Android != 7")

        var reallyDirty = 0
        for (contact in findDirtyContacts()) {
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
        }

        if (includeGroups)
            reallyDirty += findDirtyGroups().size

        return reallyDirty
    }

    fun getByGroupMembership(groupID: Long): List<LocalContact> {
        val ids = HashSet<Long>()
        provider!!.query(syncAdapterURI(ContactsContract.Data.CONTENT_URI),
                arrayOf(RawContacts.Data.RAW_CONTACT_ID),
                "(${GroupMembership.MIMETYPE}=? AND ${GroupMembership.GROUP_ROW_ID}=?) OR (${CachedGroupMembership.MIMETYPE}=? AND ${CachedGroupMembership.GROUP_ID}=?)",
                arrayOf(GroupMembership.CONTENT_ITEM_TYPE, groupID.toString(), CachedGroupMembership.CONTENT_ITEM_TYPE, groupID.toString()),
                null)?.use { cursor ->
            while (cursor.moveToNext())
                ids += cursor.getLong(0)
        }

        return ids.map { findContactByID(it) }
    }


    /* special group operations */

    /**
     * Finds the first group with the given title. If there is no group with this
     * title, a new group is created.
     * @param title title of the group to look for
     * @return id of the group with given title
     * @throws RemoteException on content provider errors
     */
    fun findOrCreateGroup(title: String): Long {
        provider!!.query(syncAdapterURI(Groups.CONTENT_URI), arrayOf(Groups._ID),
                "${Groups.TITLE}=?", arrayOf(title), null)?.use { cursor ->
            if (cursor.moveToNext())
                return cursor.getLong(0)
        }

        val values = ContentValues(1)
        values.put(Groups.TITLE, title)
        val uri = provider.insert(syncAdapterURI(Groups.CONTENT_URI), values) ?: throw RemoteException("Couldn't create contact group")
        return ContentUris.parseId(uri)
    }

    fun removeEmptyGroups() {
        // find groups without members
        /** should be done using {@link Groups.SUMMARY_COUNT}, but it's not implemented in Android yet */
        queryGroups(null, null).filter { it.getMembers().isEmpty() }.forEach { group ->
            Logger.log.log(Level.FINE, "Deleting group", group)
            group.delete()
        }
    }

}