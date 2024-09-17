/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */
package at.bitfire.davdroid.resource

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import androidx.annotation.OpenForTesting
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.SyncState
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.account.AccountUtils
import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.davdroid.util.setAndVerifyUserData
import at.bitfire.vcard4android.AndroidAddressBook
import at.bitfire.vcard4android.AndroidContact
import at.bitfire.vcard4android.AndroidGroup
import at.bitfire.vcard4android.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.LinkedList
import java.util.logging.Level
import java.util.logging.Logger

/**
 * A local address book. Requires an own Android account, because Android manages contacts per
 * account and there is no such thing as "address books". So, DAVx5 creates a "DAVx5
 * address book" account for every CardDAV address book.
 *
 * @param addressBookAccount Address book account (not: DAVx5 account) storing the actual android address book
 * @param provider Content provider needed to access and modify the address book
 */
@OpenForTesting
open class LocalAddressBook @AssistedInject constructor(
    @Assisted addressBookAccount: Account,
    @Assisted provider: ContentProviderClient,
    @ApplicationContext val context: Context,
    private val accountSettingsFactory: AccountSettings.Factory,
    private val collectionRepository: DavCollectionRepository,
    private val logger: Logger,
    private val serviceRepository: DavServiceRepository
): AndroidAddressBook<LocalContact, LocalGroup>(addressBookAccount, provider, LocalContact.Factory, LocalGroup.Factory), LocalCollection<LocalAddress> {

    companion object {

        @EntryPoint
        @InstallIn(SingletonComponent::class)
        interface LocalAddressBookCompanionEntryPoint {
            fun localAddressBookFactory(): Factory
            fun logger(): Logger
        }

        const val USER_DATA_URL = "url"
        const val USER_DATA_COLLECTION_ID = "collection_id"
        const val USER_DATA_READ_ONLY = "read_only"

        /**
         * Creates a new local address book.
         *
         * @param context        app context to resolve string resources
         * @param provider       contacts provider client
         * @param info           collection where to take the name and settings from
         * @param forceReadOnly  `true`: set the address book to "force read-only"; `false`: determine read-only flag from [info]
         */
        fun create(context: Context, provider: ContentProviderClient, info: Collection, forceReadOnly: Boolean): LocalAddressBook {
            val entryPoint = EntryPointAccessors.fromApplication<LocalAddressBookCompanionEntryPoint>(context)
            val logger = entryPoint.logger()

            val account = Account(accountName(info), context.getString(R.string.account_type_address_book))
            val userData = initialUserData(info.url.toString(), info.id.toString())
            logger.log(Level.INFO, "Creating local address book $account", userData)
            if (!AccountUtils.createAccount(context, account, userData))
                throw IllegalStateException("Couldn't create address book account")

            val factory = entryPoint.localAddressBookFactory()
            val addressBook = factory.create(account, provider)
            addressBook.updateSyncFrameworkSettings()

            // initialize Contacts Provider Settings
            val values = ContentValues(2)
            values.put(ContactsContract.Settings.SHOULD_SYNC, 1)
            values.put(ContactsContract.Settings.UNGROUPED_VISIBLE, 1)
            addressBook.settings = values
            addressBook.readOnly = forceReadOnly || !info.privWriteContent || info.forceReadOnly

            return addressBook
        }

        /**
         * Finds a [LocalAddressBook] based on its corresponding collection.
         *
         * @param info The corresponding collection. Used to calculate the address book name to look for.
         *
         * @return The [LocalAddressBook] for the given collection or *null* if not found
         */
        fun find(context: Context, provider: ContentProviderClient, info: Collection): LocalAddressBook? {
            val entryPoint = EntryPointAccessors.fromApplication<LocalAddressBookCompanionEntryPoint>(context)
            val factory = entryPoint.localAddressBookFactory()
            return AccountManager.get(context)
                .getAccountsByType(context.getString(R.string.account_type_address_book))
                .filter { account -> account.name == accountName(info) }
                .map { account -> factory.create(account, provider) }
                .firstOrNull()
        }

        /**
         * Deletes a [LocalAddressBook] based on its corresponding database collection.
         *
         * @param collection The corresponding collection
         */
        fun deleteByCollection(context: Context, collection: Collection) =
            AccountManager.get(context).run {
                getAccountsByType(context.getString(R.string.account_type_address_book)).firstOrNull { account ->
                    account.name == accountName(collection)
                }?.let { account ->
                    removeAccountExplicitly(account)
                }
            }

        /**
         * Creates a name for the address book account from its corresponding db collection info.
         *
         * The address book account name contains the collection display name or last URL segment as
         * well as the collection ID, to make the name unique.
         *
         * @param info The corresponding collection
         */
        fun accountName(info: Collection): String {
            val sb = StringBuilder(info.displayName.let {
                if (it.isNullOrEmpty())
                    info.url.lastSegment
                else
                    it
            })
            sb.append(" (${info.id})")
            return sb.toString()
        }

        private fun initialUserData(url: String, collectionId: String): Bundle {
            val bundle = Bundle(3)
            bundle.putString(USER_DATA_COLLECTION_ID, collectionId)
            bundle.putString(USER_DATA_URL, url)
            return bundle
        }

    }

    @AssistedFactory
    interface Factory {
        fun create(account: Account, provider: ContentProviderClient): LocalAddressBook
    }


    override val tag: String
        get() = "contacts-${account.name}"

    override val title = addressBookAccount.name

    /**
     * Whether contact groups ([LocalGroup]) are included in query results
     * and are affected by updates/deletes on generic members.
     *
     * For instance, if groupMethod is GROUP_VCARDS, [findDirty] will find only dirty [LocalContact]s,
     * but if it is enabled, [findDirty] will find dirty [LocalContact]s and [LocalGroup]s.
     */
    open val groupMethod: GroupMethod by lazy {
        val manager = AccountManager.get(context)
        val account = manager.getUserData(addressBookAccount, USER_DATA_COLLECTION_ID)?.toLongOrNull()?.let { collectionId ->
            collectionRepository.get(collectionId)?.let { collection ->
                serviceRepository.get(collection.serviceId)?.let { service ->
                    Account(service.accountName, context.getString(R.string.account_type))
                }
            }
        }
        if (account == null)
            throw IllegalArgumentException("Collection of address book account $addressBookAccount does not have an account")
        val accountSettings = accountSettingsFactory.create(account)
        accountSettings.getGroupMethod()
    }
    private val includeGroups
        get() = groupMethod == GroupMethod.GROUP_VCARDS

    @Deprecated("Local collection should be identified by ID, not by URL")
    override var collectionUrl: String
        get() = AccountManager.get(context).getUserData(account, USER_DATA_URL)
                ?: throw IllegalStateException("Address book has no URL")
        set(url) = AccountManager.get(context).setAndVerifyUserData(account, USER_DATA_URL, url)

    override var readOnly: Boolean
        get() = AccountManager.get(context).getUserData(account, USER_DATA_READ_ONLY) != null
        set(readOnly) = AccountManager.get(context).setAndVerifyUserData(account, USER_DATA_READ_ONLY, if (readOnly) "1" else null)

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
            number += provider!!.update(groupsSyncUri(), values, "NOT ${Groups.DIRTY}", null)
        }

        return number
    }

    override fun removeNotDirtyMarked(flags: Int): Int {
        var number = provider!!.delete(rawContactsSyncUri(),
                "NOT ${RawContacts.DIRTY} AND ${LocalContact.COLUMN_FLAGS}=?", arrayOf(flags.toString()))

        if (includeGroups)
            number += provider!!.delete(groupsSyncUri(),
                    "NOT ${Groups.DIRTY} AND ${LocalGroup.COLUMN_FLAGS}=?", arrayOf(flags.toString()))

        return number
    }

    /**
     * Updates the address book settings.
     *
     * @param info  collection where to take the settings from
     * @param forceReadOnly  `true`: set the address book to "force read-only"; `false`: determine read-only flag from [info]
     */
    fun update(info: Collection, forceReadOnly: Boolean) {
        val newAccountName = accountName(info)

        if (account.name != newAccountName) {
            // no need to re-assign contacts to new account, because they will be deleted by contacts provider in any case
            val accountManager = AccountManager.get(context)
            val future = accountManager.renameAccount(account, newAccountName, null, null)
            account = future.result
        }

        val nowReadOnly = forceReadOnly || !info.privWriteContent || info.forceReadOnly
        if (nowReadOnly != readOnly) {
            logger.info("Address book now read-only = $nowReadOnly, updating contacts")

            // update address book itself
            readOnly = nowReadOnly

            // update raw contacts
            val rawContactValues = ContentValues(1)
            rawContactValues.put(RawContacts.RAW_CONTACT_IS_READ_ONLY, if (nowReadOnly) 1 else 0)
            provider!!.update(rawContactsSyncUri(), rawContactValues, null, null)

            // update data rows
            val dataValues = ContentValues(1)
            dataValues.put(ContactsContract.Data.IS_READ_ONLY, if (nowReadOnly) 1 else 0)
            provider!!.update(syncAdapterURI(ContactsContract.Data.CONTENT_URI), dataValues, null, null)

            // update group rows
            val groupValues = ContentValues(1)
            groupValues.put(Groups.GROUP_IS_READ_ONLY, if (nowReadOnly) 1 else 0)
            provider!!.update(groupsSyncUri(), groupValues, null, null)
        }

        // make sure it will still be synchronized when contacts are updated
        updateSyncFrameworkSettings()
    }

    override fun deleteCollection(): Boolean {
        val accountManager = AccountManager.get(context)
        return accountManager.removeAccountExplicitly(account)
    }


    /**
     * Updates the sync framework settings for this address book:
     *
     * - Contacts sync of this address book account shall be possible -> isSyncable = 1
     * - When a contact is changed, a sync shall be initiated -> syncAutomatically = true
     * - Remove unwanted sync framework periodic syncs created by setSyncAutomatically, as
     * we use PeriodicSyncWorker for scheduled syncs
     */
    fun updateSyncFrameworkSettings() {
        // Enable sync-ability
        if (ContentResolver.getIsSyncable(account, ContactsContract.AUTHORITY) != 1)
            ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1)

        // Enable content trigger
        if (!ContentResolver.getSyncAutomatically(account, ContactsContract.AUTHORITY))
            ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true)

        // Remove periodic syncs (setSyncAutomatically also creates periodic syncs, which we don't want)
        for (periodicSync in ContentResolver.getPeriodicSyncs(account, ContactsContract.AUTHORITY))
            ContentResolver.removePeriodicSync(periodicSync.account, periodicSync.authority, periodicSync.extras)
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

    fun findDeletedContacts() = queryContacts(RawContacts.DELETED, null)
    fun findDeletedGroups() = queryGroups(Groups.DELETED, null)

    /**
     * Returns an array of local contacts/groups which have been changed locally (DIRTY != 0).
     * @throws RemoteException on content provider errors
     */
    override fun findDirty() =
            if (includeGroups)
                findDirtyContacts() + findDirtyGroups()
            else
                findDirtyContacts()
    fun findDirtyContacts() = queryContacts(RawContacts.DIRTY, null)
    fun findDirtyGroups() = queryGroups(Groups.DIRTY, null)

    override fun forgetETags() {
        if (includeGroups) {
            val values = ContentValues(1)
            values.putNull(AndroidGroup.COLUMN_ETAG)
            provider!!.update(groupsSyncUri(), values, null, null)
        }
        val values = ContentValues(1)
        values.putNull(AndroidContact.COLUMN_ETAG)
        provider!!.update(rawContactsSyncUri(), values, null, null)
    }


    fun getContactIdsByGroupMembership(groupId: Long): List<Long> {
        val ids = LinkedList<Long>()
        provider!!.query(syncAdapterURI(ContactsContract.Data.CONTENT_URI), arrayOf(GroupMembership.RAW_CONTACT_ID),
            "(${GroupMembership.MIMETYPE}=? AND ${GroupMembership.GROUP_ROW_ID}=?)",
            arrayOf(GroupMembership.CONTENT_ITEM_TYPE, groupId.toString()), null)?.use { cursor ->
            while (cursor.moveToNext())
                ids += cursor.getLong(0)
        }
        return ids
    }

    fun getContactUidFromId(contactId: Long): String? {
        provider!!.query(rawContactsSyncUri(), arrayOf(AndroidContact.COLUMN_UID),
            "${RawContacts._ID}=?", arrayOf(contactId.toString()), null)?.use { cursor ->
            if (cursor.moveToNext())
                return cursor.getString(0)
        }
        return null
    }


    /**
     * Queries all contacts with DIRTY flag and checks whether their data checksum has changed, i.e.
     * if they're "really dirty" (= data has changed, not only metadata, which is not hashed).
     * The DIRTY flag is removed from contacts which are not "really dirty", i.e. from contacts
     * whose contact data checksum has not changed.
     * @return number of "really dirty" contacts
     * @throws RemoteException on content provider errors
     */
    fun verifyDirty(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            throw IllegalStateException("verifyDirty() should not be called on Android != 7.0")

        var reallyDirty = 0
        for (contact in findDirtyContacts()) {
            val lastHash = contact.getLastHashCode()
            val currentHash = contact.dataHashCode()
            if (lastHash == currentHash) {
                // hash is code still the same, contact is not "really dirty" (only metadata been have changed)
                logger.log(Level.FINE, "Contact data hash has not changed, resetting dirty flag", contact)
                contact.resetDirty()
            } else {
                logger.log(Level.FINE, "Contact data has changed from hash $lastHash to $currentHash", contact)
                reallyDirty++
            }
        }

        if (includeGroups)
            reallyDirty += findDirtyGroups().size

        return reallyDirty
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
        val uri = provider!!.insert(syncAdapterURI(Groups.CONTENT_URI), values) ?: throw RemoteException("Couldn't create contact group")
        return ContentUris.parseId(uri)
    }

    fun removeEmptyGroups() {
        // find groups without members
        /** should be done using {@link Groups.SUMMARY_COUNT}, but it's not implemented in Android yet */
        queryGroups(null, null).filter { it.getMembers().isEmpty() }.forEach { group ->
            logger.log(Level.FINE, "Deleting group", group)
            group.delete()
        }
    }

}