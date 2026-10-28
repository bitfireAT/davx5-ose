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
     * Returns the Android [Account] for a given [AccountId].
     * @return the Android account for the given [accountId]
     * @throws InvalidAccountException if the account does not exist
     */
    fun getAndroidAccount(accountId: AccountId): Account {
        return when (accountId) {
            is LegacyAccount -> accountId.androidAccount
            is DbAccountId -> {
                // note: this currently depends on the account being created before. at some point, the system accounts
                //       should be dynamically created, and this method should be able to create the account if it
                //       doesn't exist yet. for now, we just throw an exception.
                dbAccountDao.getBlocking(accountId.id)
                    ?.let { Account(it.name, dbAccountType) }
                    ?: throw InvalidAccountException(accountId)
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
     * @return the [DbAccountId] if [account] is a database account (looked up by name), or a [LegacyAccount]
     *   otherwise. `null` if [account] is a database account that doesn't exist in the database.
     */
    suspend fun getAccountId(account: Account): AccountId? {
        return when (account.type) {
            dbAccountType -> dbAccountDao.getFromName(account.name)?.let { DbAccountId(it.id) }
            else -> LegacyAccount(account)
        }
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
}
