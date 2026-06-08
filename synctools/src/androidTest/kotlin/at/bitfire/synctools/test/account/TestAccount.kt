/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.test.account

import android.accounts.Account
import android.accounts.AccountManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.ical4android.TaskProvider
import at.bitfire.synctools.test.R
import at.bitfire.synctools.util.AndroidAccountUtils
import org.junit.Assert.assertTrue

object TestAccount {

    private val context by lazy { InstrumentationRegistry.getInstrumentation().context }
    private val accountType by lazy { context.getString(R.string.test_account_type) }

    /**
     * Creates a real system account backed by the androidTest-only authenticator.
     *
     * This is required for tests that need non-local dmfs task-provider behavior:
     * local accounts are deleted immediately, while synthetic non-local accounts are purged as
     * stale because the provider can't find them in [AccountManager].
     *
     * The account is made visible ([AccountManager.setAccountVisibility]) to all known [TaskProvider]s.
     */
    fun create(accountName: String = "Synctools Test Account"): Account {
        val account = Account(accountName, accountType)
        assertTrue(AndroidAccountUtils.createAccount(context, account, emptyMap()))

        if (Build.VERSION.SDK_INT >= 26) {
            val manager = AccountManager.get(context)
            for ((providerName, _) in listOf(TaskProvider.ProviderName.entries))
                if (manager.getAccountVisibility(account, providerName.packageName) != AccountManager.VISIBILITY_VISIBLE)
                    manager.setAccountVisibility(account, providerName.packageName, AccountManager.VISIBILITY_VISIBLE)
        }

        return account
    }

    fun remove(account: Account) {
        val manager = AccountManager.get(context)
        assertTrue(manager.removeAccountExplicitly(account))
    }

}
