/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */
package at.bitfire.davdroid.resource

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.Context
import android.os.Bundle
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import androidx.annotation.OpenForTesting
import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.SyncState
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.resource.workaround.ContactDirtyVerifier
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.SyncFrameworkIntegration
import at.bitfire.davdroid.sync.account.SystemAccountUtils
import at.bitfire.davdroid.sync.account.setAndVerifyUserData
import at.bitfire.vcard4android.AndroidAddressBook
import at.bitfire.vcard4android.AndroidContact
import at.bitfire.vcard4android.AndroidGroup
import at.bitfire.vcard4android.BatchOperation
import at.bitfire.vcard4android.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.LinkedList
import java.util.Optional
import java.util.logging.Level
import java.util.logging.Logger

/**
 * A local address book. Requires its own Android account, because Android manages contacts per
 * account and there is no such thing as "address books". So, DAVx5 creates a "DAVx5
 * address book" account for every CardDAV address book.
 *
 * @param account             DAVx5 account which "owns" this address book
 * @param _addressBookAccount Address book account (not: DAVx5 account) storing the actual Android
 * contacts. This is the initial value of [addressBookAccount]. However when the address book is renamed,
 * the new name will only be available in [addressBookAccount], so usually that one should be used.
 * @param provider            Content provider needed to access and modify the address book
 */
@OpenForTesting
open class LocalAddressBook @AssistedInject constructor(
    @Assisted("account") val account: Account,
    @Assisted("addressBookAccount") _addressBookAccount: Account,
    @Assisted provider: ContentProviderClient,
    private val accountSettingsFactory: AccountSettings.Factory,
    private val collectionRepository: DavCollectionRepository,
    @ApplicationContext private val context: Context,
    internal val dirtyVerifier: Optional<ContactDirtyVerifier>,
    private val logger: Logger,
    private val serviceRepository: DavServiceRepository,
    private val syncFramework: SyncFrameworkIntegration
): AndroidAddressBook<LocalContact, LocalGroup>(_addressBookAccount, provider, LocalContact.Factory, LocalGroup.Factory), LocalCollection<LocalAddress> {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("account") account: Account,
            @Assisted("addressBookAccount") addressBookAccount: Account,
            provider: ContentProviderClient
        ): LocalAddressBook
    }

    override val tag: String
        get() = "contacts-${addressBookAccount.name}"

    override val title
        get() = addressBookAccount.name

    private val accountManager by lazy { AccountManager.get(context) }

    /**
     * Whether contact groups ([LocalGroup]) are included in query results
     * and are affected by updates/deletes on generic members.
     *
     * For instance, if groupMethod is GROUP_VCARDS, [findDirty] will find only dirty [LocalContact]s,
     * but if it is enabled, [findDirty] will find dirty [LocalContact]s and [LocalGroup]s.
     */
    open val groupMethod: GroupMethod by lazy {
        val account = accountManager.getUserData(addressBookAccount, USER_DATA_COLLECTION_ID)?.toLongOrNull()?.let { collectionId ->
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
    val includeGroups
        get() = groupMethod == GroupMethod.GROUP_VCARDS

    override var dbCollectionId: Long?
        get() = accountManager.getUserData(addressBookAccount, USER_DATA_COLLECTION_ID)?.toLongOrNull() ?: throw IllegalStateException("Address book has no collection ID")
        set(id) {
            accountManager.setAndVerifyUserData(addressBookAccount, USER_DATA_COLLECTION_ID, id.toString())
        }

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
    override var readOnly: Boolean
        get() = accountManager.getUserData(addressBookAccount, USER_DATA_READ_ONLY) != null
        set(readOnly) {
            // set read-only flag for address book itself
            accountManager.setAndVerifyUserData(addressBookAccount, USER_DATA_READ_ONLY, if (readOnly) "1" else null)

            // update raw contacts
            val rawContactValues = contentValuesOf(RawContacts.RAW_CONTACT_IS_READ_ONLY to if (readOnly) 1 else 0)
            provider!!.update(rawContactsSyncUri(), rawContactValues, null, null)

            // update data rows
            val dataValues = contentValuesOf(ContactsContract.Data.IS_READ_ONLY to if (readOnly) 1 else 0)
            provider!!.update(syncAdapterURI(ContactsContract.Data.CONTENT_URI), dataValues, null, null)

            // update group rows
            val groupValues = contentValuesOf(Groups.GROUP_IS_READ_ONLY to if (readOnly) 1 else 0)
            provider!!.update(groupsSyncUri(), groupValues, null, null)
        }

    override var lastSyncState: SyncState?
        get() = syncState?.let { SyncState.fromString(String(it)) }
        set(state) {
            syncState = state?.toString()?.toByteArray()
        }


    /* operations on the collection (address book) itself */

    override fun markNotDirty(flags: Int): Int {
        val values = contentValuesOf(LocalContact.COLUMN_FLAGS to flags)
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
     * Renames an address book account and moves the contacts and groups (without making them dirty).
     * Does not keep user data of the old account, so these have to be set again.
     *
     * On success, [addressBookAccount] will be updated to the new account name.
     *
     * _Note:_ Previously, we had used [AccountManager.renameAccount], but then the contacts can't be moved because there's never
     * a moment when both accounts are available.
     *
     * @param newName   the new account name (account type is taken from [addressBookAccount])
     *
     * @return whether the account was renamed successfully
     */
    internal fun renameAccount(newName: String): Boolean {
        val oldAccount = addressBookAccount
        logger.info("Renaming address book from \"${oldAccount.name}\" to \"$newName\"")

        // create new account
        val newAccount = Account(newName, oldAccount.type)
        if (!SystemAccountUtils.createAccount(context, newAccount, Bundle()))
            return false

        // move contacts and groups to new account
        val batch = BatchOperation(provider!!)
        batch.enqueue(BatchOperation.CpoBuilder
            .newUpdate(groupsSyncUri())
            .withSelection(Groups.ACCOUNT_NAME + "=? AND " + Groups.ACCOUNT_TYPE + "=?", arrayOf(oldAccount.name, oldAccount.type))
            .withValue(Groups.ACCOUNT_NAME, newAccount.name)
        )
        batch.enqueue(BatchOperation.CpoBuilder
            .newUpdate(rawContactsSyncUri())
            .withSelection(RawContacts.ACCOUNT_NAME + "=? AND " + RawContacts.ACCOUNT_TYPE + "=?", arrayOf(oldAccount.name, oldAccount.type))
            .withValue(RawContacts.ACCOUNT_NAME, newAccount.name)
        )
        batch.commit()

        // update AndroidAddressBook.account
        addressBookAccount = newAccount

        // delete old account
        accountManager.removeAccountExplicitly(oldAccount)

        return true
    }


    /**
     * Makes contacts of this address book available to be synced and activates synchronization upon
     * contact data changes.
     */
    fun updateSyncFrameworkSettings() {
        // Enable sync-ability of contacts
        syncFramework.enableSyncAbility(addressBookAccount, ContactsContract.AUTHORITY)

        // Changes in contact data should trigger syncs
        syncFramework.enableSyncOnContentChange(addressBookAccount, ContactsContract.AUTHORITY)
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
            val values = contentValuesOf(AndroidGroup.COLUMN_ETAG to null)
            provider!!.update(groupsSyncUri(), values, null, null)
        }
        val values = contentValuesOf(AndroidContact.COLUMN_ETAG to null)
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

        val values = contentValuesOf(Groups.TITLE to title)
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


    companion object {

        const val USER_DATA_ACCOUNT_NAME = "account_name"
        const val USER_DATA_ACCOUNT_TYPE = "account_type"

        /**
         * ID of the corresponding database [at.bitfire.davdroid.db.Collection].
         *
         * User data of the address book account (Long).
         */
        const val USER_DATA_COLLECTION_ID = "collection_id"

        /**
         * Indicates whether the address book is currently set to read-only (i.e. its contacts and groups have the read-only flag).
         *
         * User data of the address book account (Boolean).
         */
        const val USER_DATA_READ_ONLY = "read_only"

    }

}