/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/
package at.bitfire.davdroid.sync.account

import android.accounts.Account
import android.accounts.AccountManager
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.R
import at.bitfire.davdroid.settings.AccountSettings
import org.junit.Assert.assertTrue

object TestAccount {

    private val context by lazy { InstrumentationRegistry.getInstrumentation().context }
    private val targetContext by lazy { InstrumentationRegistry.getInstrumentation().targetContext }

    val accountManager by lazy { AccountManager.get(context) }

    /**
     * Creates a test account, usually in the `Before` setUp of a test.
     *
     * Remove it with [remove].
     */
    fun create(version: Int = AccountSettings.CURRENT_VERSION): Account {
        val accountType = targetContext.getString(R.string.account_type)
        val account = Account("Test Account", accountType)

        val initialData = AccountSettings.initialUserData(null).apply {
            putString(AccountSettings.KEY_SETTINGS_VERSION, version.toString())
        }
        assertTrue(SystemAccountUtils.createAccount(context, account, initialData))

        return account
    }

    /**
     * Removes a test account, usually in the `@After` tearDown of a test.
     */
    fun remove(account: Account) {
        assertTrue(accountManager.removeAccountExplicitly(account))
    }

    /**
     * Convenience method to create a test account and remove it after executing the block.
     */
    fun provide(version: Int = AccountSettings.CURRENT_VERSION, block: (Account) -> Unit) {
        val account = create(version)
        try {
            block(account)
        } finally {
            remove(account)
        }
    }

}