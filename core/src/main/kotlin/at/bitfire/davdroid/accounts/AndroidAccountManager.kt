/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.accounts

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.WorkerThread
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.DbAccount
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.account.AccountOperationFailedException
import at.bitfire.davdroid.sync.account.InvalidAccountException
import at.bitfire.synctools.util.SensitiveString
import at.bitfire.synctools.util.SensitiveString.Companion.toSensitiveString
import at.bitfire.synctools.util.setAndVerifyUserData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Provider

/**
 * Creates, looks up and observes the Android [Account]s of DAVx⁵ main accounts.
 */
class AndroidAccountManager @Inject constructor(
    private val accountManager: Provider<AccountManager>,
    @ApplicationContext context: Context,
    database: AppDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val logger: Logger
) {
    private val dbAccountType = context.getString(R.string.account_type_db)

    private val dbAccountDao = database.dbAccountDao()

    /**
     * Creates an Android account and makes sure the user data are set correctly.
     *
     * @return whether the account has been created; `false` also when Android denies creation with a [SecurityException]
     */
    fun createAccount(
        account: Account,
        userData: Map<String, String> = emptyMap(),
        password: SensitiveString? = null
    ): Boolean {
        val userDataBundle = Bundle(userData.size).apply {
            for ((key, value) in userData)
                putString(key, value)
        }

        val manager = accountManager.get()
        val created = try {
            manager.addAccountExplicitly(account, password?.asString(), userDataBundle)
        } catch (e: SecurityException) {
            logger.log(Level.WARNING, "Account creation denied", e)
            try {
                val providers = manager.authenticatorTypes
                    .filter { it.type == account.type }
                    .map { it.packageName }
                logger.warning(
                    "Registered account providers: ${
                        providers.joinToString()
                            .ifEmpty { "unknown (none visible)" }
                    }"
                )
            } catch (e: RuntimeException) {
                // getAuthenticatorTypes() rethrows RemoteException as RuntimeException
                logger.log(Level.WARNING, "Registered account providers unavailable", e)
            }
            return false
        }
        if (!created)
            return false

        // Android seems to lose the initial user data sometimes, so make sure that the values are set
        for ((key, value) in userData)
            manager.setAndVerifyUserData(account, key, value)

        return true
    }

    /**
     * Creates an Android account that is linked to the DB account with ID [dbAccountId].
     *
     * Must only be called for existing DB accounts, otherwise [removeOrphanedForDbAccounts] may remove the new account.
     *
     * @return the created account, or `null` if it couldn't be created (for instance because the name is taken
     *   or there's already an account linked to [dbAccountId])
     */
    fun createLinkedAccount(name: String, dbAccountId: Long): Account? {
        if (findLinkedAccount(dbAccountId) != null)
            return null

        val account = Account(name, dbAccountType)
        return account.takeIf {
            createAccount(account, mapOf(USER_DATA_ACCOUNT_ID to dbAccountId.toString()))
        }
    }

    /**
     * Returns the Android account (for DB accounts) with the given [name], regardless of whether it's linked.
     */
    fun findAccountByName(name: String): Account? =
        accountManager.get().getAccountsByType(dbAccountType).firstOrNull { it.name == name }

    /**
     * Returns the Android account that is linked to the DB account with ID [dbAccountId].
     */
    fun findLinkedAccount(dbAccountId: Long): Account? =
        accountManager.get().getAccountsByType(dbAccountType).firstOrNull { getLinkedDbAccountId(it) == dbAccountId }

    /**
     * Returns the ID of the DB account that [account] is linked to, or `null` if it's not linked.
     */
    fun getLinkedDbAccountId(account: Account): Long? =
        accountManager.get().getUserData(account, USER_DATA_ACCOUNT_ID)?.toLongOrNull()

    /**
     * Removes the given Android account.
     */
    fun removeAccount(account: Account) {
        logger.info("Removing Android account $account")
        accountManager.get().removeAccountExplicitly(account)
    }

    /**
     * Removes the Android account associated with the given app account.
     */
    fun removeAccount(accountId: AccountId) {
        val androidAccount = getAndroidAccount(accountId)
        if (androidAccount != null) {
            removeAccount(androidAccount)
        }
    }

    /**
     * Deletes Android accounts of DB accounts that don't exist anymore. Legacy accounts are not touched.
     */
    fun removeOrphanedForDbAccounts() {
        removeAllForDbAccountsExceptFor { dbAccountDao.getAllBlocking() }
    }

    /**
     * Removes Android accounts of DB accounts that don't belong to one of the DB accounts returned by [accountsToKeep].
     * Linked accounts are matched by ID, unlinked ones by name. Legacy accounts are not touched.
     */
    @VisibleForTesting
    internal fun removeAllForDbAccountsExceptFor(accountsToKeep: () -> List<DbAccount>) {
        val manager = accountManager.get()

        // Android accounts are read before DB accounts: an Android account is only ever created after its DB account,
        // so every Android account we see also shows up in the DB accounts read afterwards. We do this to avoid
        // removing valid android accounts which may be created by the user while cleanup runs.
        val accounts = manager.getAccountsByType(dbAccountType)

        val dbAccounts = accountsToKeep()
        val existingIds = dbAccounts.map { it.id }.toSet()
        val existingNames = dbAccounts.map { it.name }.toSet()

        val orphanedAccounts = accounts.filter { account ->
            when (val linkedId = getLinkedDbAccountId(account)) {
                // not linked yet: orphaned when no DB account has its name (otherwise it's still being set up)
                null -> account.name !in existingNames
                else -> linkedId !in existingIds
            }
        }

        for (account in orphanedAccounts) {
            logger.info("Removing orphaned Android account $account of DB account")
            manager.removeAccountExplicitly(account)
        }
    }

    /**
     * Removes all Android accounts of DB accounts, including their address book accounts.
     *
     * Used when the database is (re)created, because otherwise the links (autogenerated DB row IDs) could match new DB
     * accounts.
     */
    fun removeAllForDbAccounts() = removeAllForDbAccountsExceptFor { emptyList() }

    /**
     * Returns the Android [Account] for a given [AccountId].
     *
     * For DB accounts, this is the linked Android account, which is only created on demand by [AndroidAccountProvider].
     *
     * @return the Android account for the given [accountId], or `null` if the DB account or its linked Android
     *   account doesn't exist
     */
    @WorkerThread
    fun getAndroidAccount(accountId: AccountId): Account? =
        when (accountId) {
            is LegacyAccount -> accountId.androidAccount
            is DbAccountId ->
                if (dbAccountDao.getBlocking(accountId.id) != null)
                    findLinkedAccount(accountId.id)
                else
                    null
        }

    /**
     * Like [getAndroidAccount], but throws if there's no Android account.
     *
     * @return the Android account for the given [accountId]
     * @throws InvalidAccountException if the DB account or its linked Android account doesn't exist
     */
    @WorkerThread
    fun getAndroidAccountOrThrow(accountId: AccountId): Account =
        getAndroidAccount(accountId) ?: throw InvalidAccountException(accountId)

    /**
     * Returns a flow that updates automatically whenever the account with id [accountId] is updated.
     * @param accountId The id of the account to get updates from
     * @param handler The handler to attach the listener to
     */
    fun getAndroidAccountFlow(
        accountId: AccountId,
        handler: Handler = Handler(Looper.getMainLooper())
    ): Flow<Account?> = callbackFlow {
        val accountManager = accountManager.get()

        // The listener runs on [handler] (main thread by default), so it must not access the database.
        // It only forwards the accounts; they're processed below in the io dispatcher.
        val listener = OnAccountsUpdateListener { accounts ->
            trySend(accounts.toList())
        }
        accountManager.addOnAccountsUpdatedListener(listener, handler, true)

        awaitClose {
            accountManager.removeOnAccountsUpdatedListener(listener)
        }
    }.map { accounts ->
        val account = getAndroidAccount(accountId) ?: return@map null
        // It's still possible that the account doesn't exist anymore; the list given by the account manager is authoritative
        account.takeIf { accounts.contains(it) }
    }.flowOn(ioDispatcher) // we access the database, so run the mapping in the io dispatcher

    /**
     * Returns the [AccountId] for a given Android [Account].
     *
     * @return the [DbAccountId] if [account] is a database account (looked up by link, or by name if not validly linked),
     *   or a [LegacyAccount] otherwise. `null` if [account] is a database account that doesn't exist in the database.
     */
    suspend fun getAccountId(account: Account): AccountId? {
        if (account.type != dbAccountType)
            return LegacyAccount(account)

        val linkedId = withContext(ioDispatcher) { getLinkedDbAccountId(account) }
        val dbAccount = linkedId?.let { dbAccountDao.get(it) } ?: dbAccountDao.getFromName(account.name)
        return dbAccount?.let { DbAccountId(it.id) }
    }

    /**
     * Make sure a DAVx⁵ Android account is visible to a given third-party app (jtx board, tasks app).
     */
    fun ensureAccountVisibility(accountId: AccountId, packageName: String) {
        if (Build.VERSION.SDK_INT >= 26) {
            // Warning: If setAccountVisibility is called, Android 12 broadcasts the
            // AccountManager.LOGIN_ACCOUNTS_CHANGED_ACTION Intent. This cancels running syncs and starts them again!
            // So make sure setAccountVisibility is only called when necessary.
            val accountManager = accountManager.get()
            val account = getAndroidAccountOrThrow(accountId)

            if (accountManager.getAccountVisibility(account, packageName) != AccountManager.VISIBILITY_VISIBLE) {
                accountManager.setAccountVisibility(account, packageName, AccountManager.VISIBILITY_VISIBLE)
            }
        }
    }

    /**
     * Renames an account by adding the new one, copying its data, and removing the old one.
     *
     * **Important:** Only [AccountSettings.userDataKeys] and [USER_DATA_ACCOUNT_ID] is copied from user data.
     *
     * @param oldAccount The account to rename.
     * @param newName The new name to set to the account.
     * @param onUpdate Called before removing the old account. You may perform any extra copying operations between the
     * accounts here, before the old one is gone.
     */
    suspend fun renameWithData(
        oldAccount: Account,
        newName: String,
        onUpdate: suspend (newAccount: Account) -> Unit
    ): Account = withContext(ioDispatcher + NonCancellable) {
        require(newName.isNotBlank()) { "New account name must not be blank." }

        if (oldAccount.name == newName) {
            // Names already match, no need to rename
            return@withContext oldAccount
        }

        val accountManager = accountManager.get()

        // First, check if the account already exists
        val existingAccount = accountManager.getAccountsByType(oldAccount.type).find { it.name == newName }
        if (existingAccount != null) {
            // An account already exists with the desired name
            throw IllegalStateException("An account already exists with name \"$newName\"")
        }

        val newAccount = Account(newName, oldAccount.type)

        // First, to create the new account, fetch the data from the old account
        val oldPassword: String? = accountManager.getPassword(oldAccount)
        val userDataKeys = AccountSettings.userDataKeys + USER_DATA_ACCOUNT_ID
        val oldUserData = userDataKeys.mapNotNull { key -> accountManager.getUserData(oldAccount, key)?.let { key to it } }.toMap()
        if (!createAccount(newAccount, oldUserData, oldPassword?.toSensitiveString())) {
            throw AccountOperationFailedException("New account could not be created.")
        }

        try {
            // onUpdate is now called, so that the caller can do all the movements required through the accounts
            onUpdate(newAccount)
        } catch (e: Exception) {
            // onUpdate failed, rollback
            if (!accountManager.removeAccountExplicitly(newAccount)) {
                throw AccountOperationFailedException("Could not remove account after failure on onUpdate.", e)
            }
            throw e
        }

        // When done, delete the old account
        if (!accountManager.removeAccountExplicitly(oldAccount)) {
            throw AccountOperationFailedException("Old account could not be deleted.")
        }

        newAccount
    }

    companion object {

        /** User data key of Android accounts that references the linked DB account ID */
        const val USER_DATA_ACCOUNT_ID = "account_id"

    }

}
