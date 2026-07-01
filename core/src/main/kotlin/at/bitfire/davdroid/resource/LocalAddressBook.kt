/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */
package at.bitfire.davdroid.resource

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import androidx.annotation.OpenForTesting
import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.resource.workaround.ContactDirtyVerifier
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.sync.adapter.SyncFrameworkIntegration
import at.bitfire.synctools.mapping.contacts.Contact
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.contacts.AddressContract.GroupColumns
import at.bitfire.synctools.storage.contacts.AddressContract.RawContactColumns
import at.bitfire.synctools.storage.contacts.AddressContract.asSyncAdapter
import at.bitfire.synctools.storage.contacts.AndroidAddressBook
import at.bitfire.synctools.storage.contacts.AndroidContact
import at.bitfire.synctools.storage.contacts.AndroidGroup
import at.bitfire.synctools.storage.contacts.ContactsBatchOperation
import at.bitfire.synctools.util.AndroidAccountUtils
import at.bitfire.synctools.util.setAndVerifyUserData
import at.bitfire.synctools.vcard.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.toList
import java.io.FileNotFoundException
import java.util.Optional
import java.util.logging.Logger
import kotlin.jvm.optionals.getOrNull

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
    @Assisted val groupMethod: GroupMethod,
    private val accountSettingsFactory: AccountSettings.Factory,
    @ApplicationContext private val context: Context,
    internal val dirtyVerifier: Optional<ContactDirtyVerifier>,
    private val logger: Logger,
    private val syncFramework: SyncFrameworkIntegration
) : LocalCollection<LocalAddress> {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("account") account: Account,
            @Assisted("addressBookAccount") addressBookAccount: Account,
            provider: ContentProviderClient,
            groupMethod: GroupMethod
        ): LocalAddressBook
    }

    private val accountManager by lazy { AccountManager.get(context) }

    internal val ab = AndroidAddressBook(context, _addressBookAccount, provider)

    var addressBookAccount: Account by ab::addressBookAccount

    override val tag: String
        get() = "contacts-${addressBookAccount.name}"

    override val title
        get() = addressBookAccount.name

    val includeGroups
        get() = groupMethod == GroupMethod.GROUP_VCARDS

    override var dbCollectionId: Long?
        get() = accountManager.getUserData(addressBookAccount, USER_DATA_COLLECTION_ID)?.toLongOrNull()
        set(id) {
            accountManager.setAndVerifyUserData(addressBookAccount, USER_DATA_COLLECTION_ID, id.toString())
        }

    override var readOnly: Boolean by ab::readOnly

    var settings: ContentValues by ab::settings

    var syncState: ByteArray? by ab::syncState

    override var lastSyncState: SyncState?
        get() = syncState?.let { SyncState.fromString(String(it)) }
        set(state) {
            syncState = state?.toString()?.toByteArray()
        }


    /* operations on the collection (address book) itself */

    override fun markNotDirty(flags: Int): Int {
        val batch = ContactsBatchOperation(ab.provider)
        ab.updateRawContactRows(
            contentValuesOf(RawContactColumns.FLAGS to flags),
            "${RawContacts.DIRTY}=0", null,
            batch
        )
        if (includeGroups)
            ab.updateGroups(contentValuesOf(GroupColumns.FLAGS to flags), "NOT ${Groups.DIRTY}", null, batch)
        return batch.commit()
    }

    override suspend fun removeNotDirtyMarked(flags: Int): Int {
        val batch = ContactsBatchOperation(ab.provider)
        ab.deleteRawContacts(
            "NOT ${RawContacts.DIRTY} AND ${RawContactColumns.FLAGS}=?", arrayOf(flags.toString()),
            batch
        )
        if (includeGroups)
            ab.deleteGroups("NOT ${Groups.DIRTY} AND ${GroupColumns.FLAGS}=?", arrayOf(flags.toString()), batch)
        return batch.commit()
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
        if (!AndroidAccountUtils.createAccount(context, newAccount, emptyMap()))
            return false

        // move contacts and groups to new account
        // no explicit account WHERE needed: updateGroups/updateRawContactRows scope via asSyncAdapter(addressBookAccount)
        val batch = ContactsBatchOperation(ab.provider)
        ab.updateGroups(
            contentValuesOf(Groups.ACCOUNT_NAME to newAccount.name, Groups.ACCOUNT_TYPE to newAccount.type),
            null, null, batch
        )
        ab.updateRawContactRows(
            contentValuesOf(RawContacts.ACCOUNT_NAME to newAccount.name, RawContacts.ACCOUNT_TYPE to newAccount.type),
            null, null, batch
        )
        batch.commit()

        // update addressBookAccount
        addressBookAccount = newAccount

        // delete old account
        accountManager.removeAccountExplicitly(oldAccount)

        return true
    }


    /**
     * Enables or disables sync on content changes for the address book account based on the current sync
     * interval account setting.
     */
    fun updateSyncFrameworkSettings() {
        val accountSettings = accountSettingsFactory.create(account)
        val syncInterval = accountSettings.getSyncInterval(SyncDataType.CONTACTS)

        // Enable/Disable content triggered syncs for the address book account.
        if (syncInterval != null)
            syncFramework.enableSyncOnContentChange(addressBookAccount, ContactsContract.AUTHORITY)
        else
            syncFramework.disableSyncOnContentChange(addressBookAccount, ContactsContract.AUTHORITY)
    }


    /* operations on members (contacts/groups) */

    override fun countAll(): Int {
        val contacts = ab.countRawContacts(null, null)
        val groups = if (includeGroups) ab.countGroups(null, null) else 0
        return contacts + groups
    }

    override fun countDeleted(): Int {
        val deletedContacts = ab.countRawContacts(RawContacts.DELETED, null)
        val deletedGroups = if (includeGroups) ab.countGroups(Groups.DELETED, null) else 0
        return deletedContacts + deletedGroups
    }

    override fun countModified(): Int {
        val modifiedContacts = ab.countRawContacts("${RawContacts.DIRTY} AND NOT ${RawContacts.DELETED}", null)
        val modifiedGroups = if (includeGroups) ab.countGroups("${Groups.DIRTY} AND NOT ${Groups.DELETED}", null) else 0
        return modifiedContacts + modifiedGroups
    }

    override fun countDirty(): Int {
        val dirtyContacts = ab.countRawContacts(RawContacts.DIRTY, null)
        val dirtyGroups = if (includeGroups) ab.countGroups(Groups.DIRTY, null) else 0
        return dirtyContacts + dirtyGroups
    }

    override suspend fun findByName(name: String): LocalAddress? {
        val result = queryContacts("${RawContactColumns.FILENAME}=?", arrayOf(name)).firstOrNull()
        return if (includeGroups)
            result ?: queryGroups("${GroupColumns.FILENAME}=?", arrayOf(name)).firstOrNull()
        else
            result
    }


    /**
     * Finds local contacts which have been deleted locally. (DELETED != 0).
     * @throws LocalStorageException on content provider errors
     */
    fun findDeletedContacts(): Flow<LocalContact> =
        ab.queryRawContactRows(RawContacts.DELETED, null).map { LocalContact(this, AndroidContact(ab, it)) }

    /**
     * Finds local groups which have been deleted locally. (DELETED != 0).
     * @throws LocalStorageException on content provider errors
     */
    fun findDeletedGroups(): Flow<LocalGroup> =
        ab.queryGroupRows(null, Groups.DELETED, null).map { LocalGroup(AndroidGroup(ab, it)) }

    /**
     * Finds local contacts/groups which have been deleted locally. (DELETED != 0).
     * @throws LocalStorageException on content provider errors
     */
    override fun findDeleted(): Flow<LocalAddress> =
        if (includeGroups)
            merge(findDeletedContacts(), findDeletedGroups())
        else
            findDeletedContacts()

    /**
     * Finds local contacts which have been changed locally (DIRTY != 0).
     * @throws LocalStorageException on content provider errors
     */
    fun findDirtyContacts(): Flow<LocalContact> =
        ab.queryRawContactRows(RawContacts.DIRTY, null).map { LocalContact(this, AndroidContact(ab, it)) }

    /**
     * Finds local groups which have been changed locally (DIRTY != 0).
     * @throws LocalStorageException on content provider errors
     */
    fun findDirtyGroups(): Flow<LocalGroup> =
        ab.queryGroupRows(null, Groups.DIRTY, null).map { LocalGroup(AndroidGroup(ab, it)) }

    /**
     * Finds local contacts/groups which have been changed locally (DIRTY != 0).
     * @throws LocalStorageException on content provider errors
     */
    override fun findDirty(): Flow<LocalAddress> =
        if (includeGroups)
            merge(findDirtyContacts(), findDirtyGroups())
        else
            findDirtyContacts()

    override fun forgetETags() {
        val batch = ContactsBatchOperation(ab.provider)
        if (includeGroups)
            ab.updateGroups(contentValuesOf(GroupColumns.ETAG to null), null, null, batch)
        ab.updateRawContactRows(contentValuesOf(RawContactColumns.ETAG to null), null, null, batch)
        batch.commit()
    }


    fun addContact(data: Contact, fileName: String?, eTag: String?, flags: Int): LocalContact {
        val androidContact = AndroidContact(ab, data, fileName, eTag, flags)
        androidContact.add()
        return LocalContact(this, androidContact)
    }

    fun addGroup(data: Contact, fileName: String?, eTag: String?, flags: Int): LocalGroup {
        val androidGroup = AndroidGroup(ab, data, fileName, eTag, flags)
        androidGroup.add()
        return LocalGroup(androidGroup)
    }

    suspend fun queryContacts(where: String?, whereArgs: Array<String>?): List<LocalContact> =
        ab.queryRawContactRows(where, whereArgs).map { LocalContact(this, AndroidContact(ab, it)) }.toList()

    suspend fun queryGroups(where: String?, whereArgs: Array<String>?): List<LocalGroup> =
        ab.queryGroupRows(null, where, whereArgs).map { LocalGroup(AndroidGroup(ab, it)) }.toList()

    @Throws(FileNotFoundException::class)
    fun findContactById(id: Long): LocalContact =
        ab.getRawContactRowOrNull("${RawContacts._ID}=?", arrayOf(id.toString()))
            ?.let { LocalContact(this, AndroidContact(ab, it)) }
            ?: throw FileNotFoundException()

    fun findContactByUid(uid: String): LocalContact? =
        ab.getRawContactRowOrNull("${RawContactColumns.UID}=?", arrayOf(uid))
            ?.let { LocalContact(this, AndroidContact(ab, it)) }

    @Throws(FileNotFoundException::class)
    fun findGroupById(id: Long): LocalGroup =
        ab.getGroupOrNull("${Groups._ID}=?", arrayOf(id.toString()))
            ?.let { LocalGroup(AndroidGroup(ab, it)) }
            ?: throw FileNotFoundException()


    fun getContactIdsByGroupMembership(groupId: Long): List<Long> = buildList {
        ab.provider.query(
            ContactsContract.Data.CONTENT_URI.asSyncAdapter(), arrayOf(GroupMembership.RAW_CONTACT_ID),
            "(${GroupMembership.MIMETYPE}=? AND ${GroupMembership.GROUP_ROW_ID}=?)",
            arrayOf(GroupMembership.CONTENT_ITEM_TYPE, groupId.toString()), null
        )?.use { cursor ->
            while (cursor.moveToNext())
                add(cursor.getLong(0))
        }
    }

    fun getContactUidFromId(contactId: Long): String? =
        ab.getRawContactRowOrNull("${RawContacts._ID}=?", arrayOf(contactId.toString()))
            ?.getAsString(RawContactColumns.UID)


    /* special group operations */

    /**
     * Processes all groups with pending memberships: applies them (if possible) to keep cached
     * memberships in sync.
     */
    suspend fun applyPendingMemberships() {
        logger.info("Assigning memberships of contact groups")

        queryGroups(
            "${Groups.ACCOUNT_TYPE}=? AND ${Groups.ACCOUNT_NAME}=?",
            arrayOf(addressBookAccount.type, addressBookAccount.name)
        ).forEach { group ->
            val groupId = group.id!!
            val pendingMemberUids = group.androidGroup.pendingMemberships.toMutableSet()
            val batch = ContactsBatchOperation(ab.provider)

            val changeContactIDs = HashSet<Long>()

            for (currentMemberId in getContactIdsByGroupMembership(groupId)) {
                val uid = getContactUidFromId(currentMemberId) ?: continue

                if (!pendingMemberUids.contains(uid)) {
                    logger.fine("$currentMemberId removed from group $groupId; removing group membership")
                    val currentMember = findContactById(currentMemberId)
                    currentMember.androidContact.removeGroupMemberships(batch)
                    changeContactIDs += currentMemberId
                }

                pendingMemberUids -= uid
            }

            for (missingMemberUid in pendingMemberUids) {
                val missingMember = findContactByUid(missingMemberUid)
                if (missingMember == null) {
                    logger.warning("Group $groupId has member $missingMemberUid which is not found in the address book; ignoring")
                    continue
                }

                logger.fine("Assigning member $missingMember to group $groupId")
                missingMember.androidContact.addToGroup(batch, groupId)
                changeContactIDs += missingMember.id!!
            }

            dirtyVerifier.getOrNull()?.let { verifier ->
                // workaround for Android 7 which sets DIRTY flag when only meta-data is changed
                changeContactIDs
                    .map { id -> findContactById(id) }
                    .forEach { contact -> verifier.updateHashCode(contact, batch) }
            }

            batch.commit()
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

    }

}
