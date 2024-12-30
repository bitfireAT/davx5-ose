package at.bitfire.davdroid.sync.account

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.os.Bundle
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.testing.TestListenableWorkerBuilder
import at.bitfire.davdroid.R
import at.bitfire.davdroid.TestUtils
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import at.bitfire.davdroid.db.Account as DbAccount

@HiltAndroidTest
class AccountsCleanupWorkerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var accountsCleanupWorkerFactory: AccountsCleanupWorker.Factory

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    lateinit var account: Account
    lateinit var service: Service

    val accountManager by lazy { AccountManager.get(context) }
    lateinit var addressBookAccountType: String
    lateinit var addressBookAccount: Account

    @Before
    fun setUp() {
        hiltRule.inject()
        TestUtils.setUpWorkManager(context, workerFactory)

        account = TestAccountAuthenticator.create()
        db.accountDao().insertOrIgnore(DbAccount(name = account.name))

        // Prepare test account
        addressBookAccountType = context.getString(R.string.account_type_address_book)
        addressBookAccount = Account(
            "Fancy address book account",
            addressBookAccountType
        )
    }

    @After
    fun tearDown() {
        // Remove the account here in any case; Nice to have when the test fails
        accountManager.removeAccountExplicitly(addressBookAccount)

        TestAccountAuthenticator.remove(account)
    }


    @Test
    fun testCleanUpAddressBooks_deletesAddressBookWithoutAccount() {
        // Create address book account without corresponding account
        assertTrue(accountManager.addAccountExplicitly(addressBookAccount, null, null))

        val addressBookAccounts = accountManager.getAccountsByType(addressBookAccountType)
        assertEquals(addressBookAccount, addressBookAccounts.firstOrNull())

        // Create worker and run the method
        val worker = TestListenableWorkerBuilder<AccountsCleanupWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()
        worker.cleanUpAddressBooks()

        // Verify account was deleted
        assertTrue(accountManager.getAccountsByType(addressBookAccountType).isEmpty())
    }

    @Test
    fun testCleanUpAddressBooks_keepsAddressBookWithAccount() {
        TestAccountAuthenticator.provide { account ->
            // Create address book account _with_ corresponding account and verify
            val userData = Bundle(2).apply {
                putString(LocalAddressBook.USER_DATA_ACCOUNT_NAME, account.name)
                putString(LocalAddressBook.USER_DATA_ACCOUNT_TYPE, account.type)
            }
            assertTrue(accountManager.addAccountExplicitly(addressBookAccount, null, userData))

            val addressBookAccounts = accountManager.getAccountsByType(addressBookAccountType)
            assertEquals(addressBookAccount, addressBookAccounts.firstOrNull())

            // Create worker and run the method
            val worker = TestListenableWorkerBuilder<AccountsCleanupWorker>(context)
                .setWorkerFactory(workerFactory)
                .build()
            worker.cleanUpAddressBooks()

            // Verify account was _not_ deleted
            assertEquals(addressBookAccount, addressBookAccounts.firstOrNull())
        }
    }

}