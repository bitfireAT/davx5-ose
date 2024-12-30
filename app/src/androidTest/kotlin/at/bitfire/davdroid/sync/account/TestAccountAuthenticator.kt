/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/
package at.bitfire.davdroid.sync.account

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.AccountManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.test.R
import org.junit.Assert.assertTrue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Handles the test account type, which has no sync adapters and side effects that run unintentionally.
 *
 * Usually used like this:
 *
 * ```
 * lateinit var account: Account
 *
 * @Before
 * fun setUp() {
 *     account = TestAccountAuthenticator.create()
 *
 *     // You can now use the test account.
 * }
 *
 * @After
 * fun tearDown() {
 *    TestAccountAuthenticator.remove(account)
 * }
 * ```
 */
class TestAccountAuthenticator: Service() {

    companion object {

        val context by lazy { InstrumentationRegistry.getInstrumentation().context }
        val counter = AtomicInteger(0)

        /**
         * Creates a test account, usually in the `Before` setUp of a test.
         *
         * Remove it with [remove].
         */
        fun create(version: Int = AccountSettings.CURRENT_VERSION, accountType: String = context.getString(R.string.account_type_test)): Account {
            val account = Account("Test Account No. ${counter.incrementAndGet()}", accountType)

            val initialData = AccountSettings.initialUserData(null)
            initialData.putString(AccountSettings.KEY_SETTINGS_VERSION, version.toString())
            assertTrue(SystemAccountUtils.createAccount(context, account, initialData))

            return account
        }

        /**
         * Removes a test account, usually in the `@After` tearDown of a test.
         */
        fun remove(account: Account) {
            val am = AccountManager.get(context)
            assertTrue(am.removeAccountExplicitly(account))
        }

        /**
         * Convenience method to create a test account and remove it after executing the block.
         */
        fun provide(version: Int = AccountSettings.CURRENT_VERSION, accountType: String = context.getString(R.string.account_type_test), block: (Account) -> Unit) {
            val account = create(version, accountType)
            try {
                block(account)
            } finally {
                remove(account)
            }
        }

    }


    private lateinit var accountAuthenticator: AccountAuthenticator


    override fun onCreate() {
        accountAuthenticator = AccountAuthenticator(this)
    }

    override fun onBind(intent: Intent?) =
            accountAuthenticator.iBinder.takeIf { intent?.action == AccountManager.ACTION_AUTHENTICATOR_INTENT }


    private class AccountAuthenticator(
        val context: Context
    ): AbstractAccountAuthenticator(context) {

        override fun addAccount(response: AccountAuthenticatorResponse?, accountType: String?, authTokenType: String?, requiredFeatures: Array<String>?, options: Bundle?) = null
        override fun editProperties(response: AccountAuthenticatorResponse?, accountType: String?) = null
        override fun getAuthTokenLabel(p0: String?) = null
        override fun confirmCredentials(p0: AccountAuthenticatorResponse?, p1: Account?, p2: Bundle?) = null
        override fun updateCredentials(p0: AccountAuthenticatorResponse?, p1: Account?, p2: String?, p3: Bundle?) = null
        override fun getAuthToken(p0: AccountAuthenticatorResponse?, p1: Account?, p2: String?, p3: Bundle?) = null
        override fun hasFeatures(p0: AccountAuthenticatorResponse?, p1: Account?, p2: Array<out String>?) = null

    }

}