/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.local

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.content.Context
import android.os.Bundle
import at.bitfire.davdroid.R
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.DbAccountId
import at.bitfire.davdroid.accounts.LegacyAccount
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.synctools.util.setAndVerifyUserData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Deals with reading and writing user data of an address book account.
 */
class AddressBookAccountManager @Inject constructor(
    private val accountManager: AccountManager,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger,
) {
    private val mainLegacyAccountType = context.getString(R.string.account_type)
    private val mainDbAccountType = context.getString(R.string.account_type_db)
    private val addressBookAccountType = context.getString(R.string.account_type_address_book)

    /**
     * Create an address book account.
     *
     * @param accountId The [AccountId] of the app account the address book account should be linked to.
     * @param name The name of the address book account
     * @param collectionId The database ID of the collection the address book account should be linked to.
     */
    fun create(accountId: AccountId, name: String, collectionId: Long): Account? {
        // create address book account with reference to account, collection ID and URL
        val addressBookAccount = Account(name, addressBookAccountType)

        val userData = Bundle().apply {
            when (accountId) {
                is LegacyAccount -> {
                    putString(USER_DATA_ACCOUNT_NAME, accountId.androidAccount.name)
                    putString(USER_DATA_ACCOUNT_TYPE, accountId.androidAccount.type)
                }
                is DbAccountId -> {
                    putString(USER_DATA_ACCOUNT_ID, accountId.id.toString())
                    putString(USER_DATA_ACCOUNT_TYPE, mainDbAccountType)
                }
            }
            putString(USER_DATA_COLLECTION_ID, collectionId.toString())
        }

        try {
            if (!accountManager.addAccountExplicitly(addressBookAccount, null, userData)) {
                logger.log(Level.WARNING, "Couldn't create address book account: $addressBookAccount")
                return null
            }
        } catch (e: SecurityException) {
            logger.log(Level.WARNING, "Couldn't create address book account: $addressBookAccount", e)
            logAccountProviders(addressBookAccount)
            return null
        }

        // Android seems to lose the initial user data sometimes, so make sure that the values are set
        setAppAccount(addressBookAccount, accountId)
        setCollectionId(addressBookAccount, collectionId)

        return addressBookAccount
    }

    private fun logAccountProviders(account: Account) {
        try {
            val providers = accountManager.authenticatorTypes
                .filter { it.type == account.type }
                .map { it.packageName }

            val registeredAccountProviders = providers.joinToString().ifEmpty { "unknown (none visible)" }
            logger.warning("Registered account providers: $registeredAccountProviders")
        } catch (e: RuntimeException) {
            // getAuthenticatorTypes() rethrows RemoteException as RuntimeException
            logger.log(Level.WARNING, "Registered account providers unavailable", e)
        }
    }

    /**
     * Deletes the given address book account.
     */
    fun delete(account: Account) {
        accountManager.removeAccountExplicitly(account)
    }

    /**
     * Deletes all address book accounts linked to the given app account.
     */
    fun deleteByAccountId(accountId: AccountId) {
        forEachLinkedTo(accountId) { addressBookAccount ->
            accountManager.removeAccountExplicitly(addressBookAccount)
        }
    }

    /**
     * Deletes all address book accounts linked to a database account.
     *
     * Used when the database is (re)created: then the remaining accounts are orphaned and their links (DB IDs)
     * could otherwise match new DB accounts.
     */
    fun deleteAllLinkingDbAccount() {
        for (account in accountManager.getAccountsByType(addressBookAccountType)) {
            if (getAppAccount(account) is DbAccountId) {
                delete(account)
            }
        }
    }

    /**
     * Updates address book accounts which are linked to [oldAccountId] to be linked to [newAccountId] instead.
     */
    fun updateAppAccount(oldAccountId: AccountId, newAccountId: AccountId) {
        if (oldAccountId == newAccountId) return

        forEachLinkedTo(oldAccountId) { addressBookAccount ->
            setAppAccount(addressBookAccount, newAccountId)
        }
    }

    fun updateProperties(account: Account, accountId: AccountId, collectionId: Long) {
        setAppAccount(account, accountId)
        setCollectionId(account, collectionId)
    }

    /**
     * Returns all address book accounts.
     */
    fun getAll(): List<Account> {
        return accountManager.getAccountsByType(addressBookAccountType).toList()
    }

    /**
     * Returns all address book accounts that belong to the given app account.
     */
    fun getAllByAccountId(accountId: AccountId): List<Account> {
        return accountManager.getAccountsByType(addressBookAccountType)
            .filter { addressBookAccount ->
                getAppAccount(addressBookAccount) == accountId
            }
    }

    /**
     * Returns all address book accounts that belong to the given app account in a flow.
     */
    fun getAllByAccountIdFlow(accountId: AccountId): Flow<List<Account>> {
        return callbackFlow {
            val listener = OnAccountsUpdateListener { _ ->
                trySend(Unit)
            }

            accountManager.addOnAccountsUpdatedListener(
                /* listener = */ listener,
                /* handler = */ null,
                /* updateImmediately = */ true
            )

            awaitClose { accountManager.removeOnAccountsUpdatedListener(listener) }
        }
            .map { getAllByAccountId(accountId) }
            .distinctUntilChanged()
            .flowOn(ioDispatcher)
    }

    /**
     * Get [AccountId] of the app account that the given address book account belongs to.
     */
    fun getAppAccount(account: Account): AccountId? {
        requireAddressBookAccount(account)

        val ownerId = accountManager.getUserData(account, USER_DATA_ACCOUNT_ID)?.toLongOrNull()
        val ownerName = accountManager.getUserData(account, USER_DATA_ACCOUNT_NAME)
        val ownerType = accountManager.getUserData(account, USER_DATA_ACCOUNT_TYPE)

        return if (ownerName != null) {
            if (ownerType == mainLegacyAccountType) {
                LegacyAccount(Account(ownerName, ownerType))
            } else {
                null
            }
        } else if (ownerId != null) {
            if (ownerType == mainDbAccountType) {
                DbAccountId(ownerId)
            } else {
                null
            }
        } else {
            null
        }
    }

    /**
     * Set [AccountId] of the app account that the given address book account belongs to.
     */
    fun setAppAccount(account: Account, owner: AccountId) {
        requireAddressBookAccount(account)

        when (owner) {
            is LegacyAccount -> {
                accountManager.setAndVerifyUserData(account, USER_DATA_ACCOUNT_ID, null)
                accountManager.setAndVerifyUserData(account, USER_DATA_ACCOUNT_NAME, owner.androidAccount.name)
                accountManager.setAndVerifyUserData(account, USER_DATA_ACCOUNT_TYPE, owner.androidAccount.type)
            }
            is DbAccountId -> {
                accountManager.setAndVerifyUserData(account, USER_DATA_ACCOUNT_ID, owner.id.toString())
                accountManager.setAndVerifyUserData(account, USER_DATA_ACCOUNT_NAME, null)
                accountManager.setAndVerifyUserData(account, USER_DATA_ACCOUNT_TYPE, mainDbAccountType)
            }
        }
    }

    /**
     * Get the collection ID for the given address book account.
     */
    fun getCollectionId(account: Account): Long? {
        requireAddressBookAccount(account)

        return accountManager.getUserData(account, USER_DATA_COLLECTION_ID)?.toLongOrNull()
    }

    /**
     * Set the collection ID for the given address book account.
     */
    fun setCollectionId(account: Account, collectionId: Long?) {
        requireAddressBookAccount(account)

        accountManager.setAndVerifyUserData(account, USER_DATA_COLLECTION_ID, collectionId?.toString())
    }

    /**
     * Get a named property for the given address book account.
     */
    fun getProperty(account: Account, key: String): String? {
        requireAddressBookAccount(account)

        return accountManager.getUserData(account, key)
    }

    /**
     * Set a named property for the given address book account.
     */
    fun setProperty(account: Account, key: String, value: String?) {
        requireAddressBookAccount(account)

        accountManager.setAndVerifyUserData(account, key, value)
    }

    private fun requireAddressBookAccount(account: Account) {
        require(account.type == addressBookAccountType) { "Account.type needs to be $addressBookAccountType" }
    }

    private fun forEachLinkedTo(accountId: AccountId, block: (Account) -> Unit) {
        return accountManager.getAccountsByType(addressBookAccountType)
            .asSequence()
            .filter { addressBookAccount -> getAppAccount(addressBookAccount) == accountId }
            .forEach(block)
    }


    companion object {
        private const val USER_DATA_ACCOUNT_ID = "account_id"
        private const val USER_DATA_ACCOUNT_NAME = "account_name"
        private const val USER_DATA_ACCOUNT_TYPE = "account_type"

        /**
         * ID of the corresponding database [at.bitfire.davdroid.db.Collection].
         *
         * User data of the address book account (Long).
         */
        private const val USER_DATA_COLLECTION_ID = "collection_id"
    }
}
