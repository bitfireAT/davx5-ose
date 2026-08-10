/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.accounts

import android.accounts.Account
import android.accounts.AccountManager
import android.os.Build
import javax.inject.Inject
import javax.inject.Provider

/**
 * A class to manage Android [Account]s.
 */
class AndroidAccountManager @Inject constructor(
    private val accountManager: Provider<AccountManager>
) {
    fun getAndroidAccount(accountId: AccountId): Account {
        return when (accountId) {
            is LegacyAccount -> accountId.androidAccount
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
