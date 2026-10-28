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
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.sync.account.InvalidAccountException
import at.bitfire.synctools.util.SensitiveString
import at.bitfire.synctools.util.setAndVerifyUserData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Provider

/**
 * Creates, looks up and observes the Android [Account]s of DAVx⁵ (main and address book accounts).
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
     * @return the created account, or `null` if it couldn't be created (for instance because the name is taken)
     */
    fun createLinkedAccount(name: String, dbAccountId: Long): Account? {
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
     * Links [account] to the DB account with ID [dbAccountId].
     */
    fun linkAccount(account: Account, dbAccountId: Long) {
        accountManager.get().setAndVerifyUserData(account, USER_DATA_ACCOUNT_ID, dbAccountId.toString())
    }

    /**
     * Returns the Android [Account] for a given [AccountId].
     *
     * For DB accounts, this is the linked Android account. If there's none (yet), an account with the DB account
     * name is returned, which doesn't necessarily exist.
     *
     * @return the Android account for the given [accountId]
     * @throws InvalidAccountException if the account does not exist
     */
    fun getAndroidAccount(accountId: AccountId): Account {
        return when (accountId) {
            is LegacyAccount -> accountId.androidAccount
            is DbAccountId -> {
                val dbAccount = dbAccountDao.getBlocking(accountId.id) ?: throw InvalidAccountException(accountId)
                // fallback: name that AndroidAccountProvider will use when creating the account
                findLinkedAccount(accountId.id) ?: Account(dbAccount.name, dbAccountType)
            }
        }
    }

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

        val listener = OnAccountsUpdateListener { accounts ->
            val account = try {
                getAndroidAccount(accountId)
            } catch (_: InvalidAccountException) {
                // DB Account not found
                null
            }
            // If account was not found, emit null
            if (account == null) {
                trySend(null)
                return@OnAccountsUpdateListener
            }
            // Once here, it's still possible that the account doesn't exist anymore, check it
            if (!accounts.contains(account)) {
                // The updated accounts list that account manager gave us doesn't contain the account: it no longer exists
                trySend(null)
                return@OnAccountsUpdateListener
            }
            // Finally, the account does indeed exist, emit it
            trySend(account)
        }
        accountManager.addOnAccountsUpdatedListener(listener, handler, true)

        awaitClose {
            accountManager.removeOnAccountsUpdatedListener(listener)
        }
    }.flowOn(ioDispatcher) // we access the database, so the run the flow in the io dispatcher

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
            val account = getAndroidAccount(accountId)

            if (accountManager.getAccountVisibility(account, packageName) != AccountManager.VISIBILITY_VISIBLE) {
                accountManager.setAccountVisibility(account, packageName, AccountManager.VISIBILITY_VISIBLE)
            }
        }
    }

    companion object {

        /** User data key of Android accounts that references the linked DB account ID */
        const val USER_DATA_ACCOUNT_ID = "account_id"

    }

}
