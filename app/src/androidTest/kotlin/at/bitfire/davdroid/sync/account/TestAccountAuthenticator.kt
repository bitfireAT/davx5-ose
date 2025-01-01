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
class TestAccountAuthenticator {

    companion object {

        private val context by lazy { InstrumentationRegistry.getInstrumentation().context }
        private val targetContext by lazy { InstrumentationRegistry.getInstrumentation().targetContext }

        private val accountType by lazy { targetContext.getString(R.string.account_type) }

        val counter = AtomicInteger(0)

        /**
         * Creates a test account, usually in the `Before` setUp of a test.
         *
         * Remove it with [remove].
         */
        fun create(version: Int = AccountSettings.CURRENT_VERSION): Account {
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
        fun provide(version: Int = AccountSettings.CURRENT_VERSION, block: (Account) -> Unit) {
            val account = create(version)
            try {
                block(account)
            } finally {
                remove(account)
            }
        }

    }

}