/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.content.ContentProviderClient
import android.content.Context
import android.provider.ContactsContract
import androidx.annotation.OpenForTesting
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.R
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.AndroidAccountManager
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.settings.AccountSettingsFactory
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.synctools.util.AndroidAccountUtils
import com.google.common.base.CharMatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Provider

class LocalAddressBookStore @Inject constructor(
    private val accountManager: Provider<AccountManager>,
    private val accountRepository: AccountRepository,
    private val accountSettingsFactory: AccountSettingsFactory,
    private val addressBookAccountProperties: AddressBookAccountProperties,
    private val androidAccountManager: AndroidAccountManager,
    @ApplicationContext private val context: Context,
    private val localAddressBookFactory: LocalAddressBook.Factory,
    private val logger: Logger,
    private val serviceRepository: DavServiceRepository,
    private val settings: SettingsManager
): LocalDataStore<LocalAddressBook> {

    override val authority: String
        get() = ContactsContract.AUTHORITY

    /** whether a (usually managed) setting wants all address-books to be read-only **/
    val forceAllReadOnly: Boolean
        get() = settings.getBoolean(Settings.FORCE_READ_ONLY_ADDRESSBOOKS)


    /**
     * Assembles a name for the address book (account) from its corresponding database [Collection].
     *
     * The address book account name contains
     *
     * - the collection display name or last URL path segment (filtered for dangerous special characters)
     * - the actual account name
     * - the collection ID, to make it unique.
     *
     * @param info  Collection to take info from
     */
    @WorkerThread
    fun accountName(info: Collection): String {
        // Name of address book is given collection display name, otherwise the last URL path segment
        var name = info.displayName.takeIf { !it.isNullOrEmpty() } ?: info.url.lastSegment.ifEmpty { "/" }

        // Remove ISO control characters + SQL problematic characters
        name = CharMatcher
            .javaIsoControl()
            .or(CharMatcher.anyOf("`'\""))
            .removeFrom(name)

        // Add the actual account name to the address book account name
        val sb = StringBuilder(name)
        serviceRepository.getBlocking(info.serviceId)?.let { service ->
            sb.append(" (${service.accountName})")
        }
        // Add the collection ID for uniqueness
        sb.append(" #${info.id}")
        return sb.toString()
    }

    override fun acquireContentProvider(throwOnMissingPermissions: Boolean) = try {
        context.contentResolver.acquireContentProviderClient(authority)
    } catch (e: SecurityException) {
        if (throwOnMissingPermissions)
            throw e
        else
            /* return */ null
    }

    override suspend fun create(client: ContentProviderClient, fromCollection: Collection): LocalAddressBook? {
        val service = serviceRepository.get(fromCollection.serviceId)
            ?: throw IllegalArgumentException("Couldn't fetch DB service from collection")
        val accountId = accountRepository.getAccountIdFromName(service.accountName)

        val name = accountName(fromCollection)
        val addressBookAccount = createAddressBookAccount(
            accountId = accountId,
            name = name,
            id = fromCollection.id
        ) ?: return null

        val accountSettings = accountSettingsFactory.create(accountId)
        val addressBook = localAddressBookFactory.create(
            accountId = accountId,
            addressBookAccount = addressBookAccount,
            provider = client,
            groupMethod = accountSettings.getGroupMethod()
        )

        // update settings
        addressBook.updateSyncFrameworkSettings()
        addressBook.settings = contactsProviderSettings
        addressBook.readOnly = shouldBeReadOnly(fromCollection, forceAllReadOnly)

        return addressBook
    }

    @OpenForTesting
    internal fun createAddressBookAccount(accountId: AccountId, name: String, id: Long): Account? {
        // create address book account with reference to account, collection ID and URL
        val addressBookAccount = Account(name, context.getString(R.string.account_type_address_book))
        if (!AndroidAccountUtils.createAccount(context, addressBookAccount)) {
            logger.warning("Couldn't create address book account: $addressBookAccount")
            return null
        }

        addressBookAccountProperties.setAppAccount(addressBookAccount, accountId)
        addressBookAccountProperties.setCollectionId(addressBookAccount, id)

        return addressBookAccount
    }

    override fun getAll(accountId: AccountId, client: ContentProviderClient): List<LocalAddressBook> {
        val accountSettings = accountSettingsFactory.create(accountId)
        val groupMethod = accountSettings.getGroupMethod()
        return getAddressBookAccounts(accountId).map { addressBookAccount ->
            localAddressBookFactory.create(accountId, addressBookAccount, client, groupMethod)
        }
    }

    override fun getByDbCollectionId(
        accountId: AccountId,
        client: ContentProviderClient,
        dbCollectionId: Long
    ): LocalAddressBook? {
        return getAll(accountId, client).firstOrNull { it.dbCollectionId == dbCollectionId }
    }

    override fun update(
        accountId: AccountId,
        client: ContentProviderClient,
        localCollection: LocalAddressBook,
        fromCollection: Collection
    ) {
        var currentAccount = localCollection.addressBookAccount
        logger.info("Updating local address book $currentAccount from collection $fromCollection")

        // Update the account name
        val newAccountName = accountName(fromCollection)
        if (currentAccount.name != newAccountName) {
            // rename, move contacts/groups and update [AndroidAddressBook.]account
            localCollection.renameAccount(newAccountName)
            currentAccount = Account(newAccountName, currentAccount.type)
        }

        // Update the account user data
        addressBookAccountProperties.setAppAccount(currentAccount, accountId)
        addressBookAccountProperties.setCollectionId(currentAccount, fromCollection.id)

        // Set contacts provider settings
        localCollection.settings = contactsProviderSettings

        // Update force read only
        val nowReadOnly = shouldBeReadOnly(fromCollection, forceAllReadOnly)
        if (nowReadOnly != localCollection.readOnly) {
            logger.info("Address book has changed to read-only = $nowReadOnly")
            localCollection.readOnly = nowReadOnly
        }

        // Update automatic synchronization
        localCollection.updateSyncFrameworkSettings()
    }

    /**
     * Updates address books which are assigned to [oldAccount] so that they're assigned to [newAccount] instead.
     *
     * @param oldAccount    The old account
     * @param newAccount    The new account
     * @param client        content provider client (not needed/does not exist for address books)
     */
    override fun updateAccount(oldAccount: Account, newAccount: Account, client: ContentProviderClient?) {
        val oldAccountId = androidAccountManager.getAccountId(oldAccount)
        val newAccountId = androidAccountManager.getAccountId(newAccount)

        accountManager.get().getAccountsByType(context.getString(R.string.account_type_address_book))
            .filter { addressBookAccount ->
                addressBookAccountProperties.getAppAccount(addressBookAccount) == oldAccountId
            }
            .forEach { addressBookAccount ->
                addressBookAccountProperties.setAppAccount(addressBookAccount, newAccountId)
            }
    }

    override fun delete(localCollection: LocalAddressBook) {
        accountManager.get().removeAccountExplicitly(localCollection.addressBookAccount)
    }

    /**
     * Deletes a [LocalAddressBook] based on its corresponding database collection.
     *
     * @param id    [Collection.id] to look for
     */
    fun deleteByCollectionId(id: Long) {
        val accountManager = accountManager.get()
        val addressBookAccount = accountManager.getAccountsByType(context.getString(R.string.account_type_address_book)).firstOrNull { account ->
            addressBookAccountProperties.getCollectionId(account) == id
        }
        if (addressBookAccount != null)
            accountManager.removeAccountExplicitly(addressBookAccount)
    }

    /**
     * Returns all address book accounts that belong to the given account.
     *
     * @param accountId [AccountId] of the app account that owns the address books.
     * @return List of address book accounts.
     */
    fun getAddressBookAccounts(accountId: AccountId): List<Account> =
        accountManager.get().getAccountsByType(context.getString(R.string.account_type_address_book))
            .filter { addressBookAccount ->
                addressBookAccountProperties.getAppAccount(addressBookAccount) == accountId
            }

    /**
     * Returns all address book accounts that belong to the given account in a flow.
     *
     * @param accountId [AccountId] of the app account that owns the address books.
     * @return List of address book accounts as flow.
     */
    fun getAddressBookAccountsFlow(accountId: AccountId): Flow<List<Account>> = callbackFlow {
        val accountManager = accountManager.get()
        val listener = OnAccountsUpdateListener { _ ->
            trySend(getAddressBookAccounts(accountId))
        }
        accountManager.addOnAccountsUpdatedListener(
            /* listener = */ listener,
            /* handler = */ null,
            /* updateImmediately = */ true
        )
        awaitClose { accountManager.removeOnAccountsUpdatedListener(listener) }
    }


    companion object {

        /**
         * Contacts Provider Settings (equal for every address book)
         */
        val contactsProviderSettings
            get() = contentValuesOf(
                // SHOULD_SYNC is just a hint that an account's contacts (the contacts of this local address book) are syncable.
                ContactsContract.Settings.SHOULD_SYNC to 1,

                // UNGROUPED_VISIBLE is required for making contacts work over Bluetooth (especially with some car systems).
                ContactsContract.Settings.UNGROUPED_VISIBLE to 1
            )

        /**
         * Determines whether the address book should be set to read-only.
         *
         * @param forceAllReadOnly  Whether (usually managed, app-wide) setting should overwrite local read-only information
         * @param info              Collection data to determine read-only status from (either user-set read-only flag or missing write privilege)
         */
        @VisibleForTesting
        internal fun shouldBeReadOnly(info: Collection, forceAllReadOnly: Boolean): Boolean =
            info.readOnly() || forceAllReadOnly

    }

}