/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.accounts

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.os.Build
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.sync.account.InvalidAccountException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Provider

/**
 * A class to manage Android [Account]s.
 */
class AndroidAccountManager @Inject constructor(
    private val accountManager: Provider<AccountManager>,
    @ApplicationContext context: Context,
    database: AppDatabase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val accountType = context.getString(R.string.account_type)

    private val dbAccountDao = database.dbAccountDao()

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
                // note: currently, this is using runBlocking. ideally we should make the function suspending
                runBlocking(ioDispatcher) { dbAccountDao.get(accountId.id) }
                    ?.let { Account(it.name, accountType) }
                    ?: throw InvalidAccountException(accountId)
            }
        }
    }

    fun getAccountId(account: Account): AccountId {
        return LegacyAccount(account)
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
