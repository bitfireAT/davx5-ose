/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.test.account

import android.accounts.Account
import android.accounts.AccountManager
import androidx.test.platform.app.InstrumentationRegistry
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
     */
    fun create(accountName: String = "Synctools Test Account"): Account {
        val account = Account(accountName, accountType)
        assertTrue(AndroidAccountUtils.createAccount(context, account, emptyMap()))
        return account
    }

    fun remove(account: Account) {
        val manager = AccountManager.get(context)
        assertTrue(manager.removeAccountExplicitly(account))
    }

}
