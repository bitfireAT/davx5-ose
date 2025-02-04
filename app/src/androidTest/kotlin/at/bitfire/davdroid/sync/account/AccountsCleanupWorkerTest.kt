/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.account

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.testing.TestListenableWorkerBuilder
import at.bitfire.davdroid.R
import at.bitfire.davdroid.TestUtils
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.resource.LocalTestAddressBook
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class AccountsCleanupWorkerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var accountsCleanupWorkerFactory: AccountsCleanupWorker.Factory

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var db: AppDatabase

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    lateinit var accountManager: AccountManager
    lateinit var addressBookAccountType: String
    lateinit var addressBookAccount: Account
    lateinit var service: Service

    @Before
    fun setUp() {
        ContentResolver.setMasterSyncAutomatically(false)
        hiltRule.inject()
        TestUtils.setUpWorkManager(context, workerFactory)

        accountManager = AccountManager.get(context)
        service = createTestService()

        addressBookAccountType = context.getString(R.string.account_type_address_book)
        addressBookAccount = Account("Fancy address book account", addressBookAccountType)

        // Make sure there are no address books
        LocalTestAddressBook.removeAll(context)
    }

    @After
    fun tearDown() {
        // Remove the account here in any case; Nice to have when the test fails
        accountManager.removeAccountExplicitly(addressBookAccount)
    }


    @Test
    fun testCleanUpServices_noAccount() {
        // Insert service that reference to invalid account
        db.serviceDao().insertOrReplace(Service(id = 1, accountName = "test", type = Service.TYPE_CALDAV, principal = null))
        assertNotNull(db.serviceDao().get(1))

        // Create worker and run the method
        val worker = TestListenableWorkerBuilder<AccountsCleanupWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()
        worker.cleanUpServices()

        // Verify that service is deleted
        assertNull(db.serviceDao().get(1))
    }

    @Test
    fun testCleanUpServices_oneAccount() {
        TestAccount.provide { existingAccount ->
            // Insert services, one that reference the existing account and one that references an invalid account
            db.serviceDao().insertOrReplace(Service(id = 1, accountName = existingAccount.name, type = Service.TYPE_CALDAV, principal = null))
            assertNotNull(db.serviceDao().get(1))

            db.serviceDao().insertOrReplace(Service(id = 2, accountName = "not existing", type = Service.TYPE_CARDDAV, principal = null))
            assertNotNull(db.serviceDao().get(2))

            // Create worker and run the method
            val worker = TestListenableWorkerBuilder<AccountsCleanupWorker>(context)
                .setWorkerFactory(workerFactory)
                .build()
            worker.cleanUpServices()

            // Verify that one service is deleted and the other one is kept
            assertNotNull(db.serviceDao().get(1))
            assertNull(db.serviceDao().get(2))
        }
    }


    @Test
    fun testCleanUpAddressBooks_deletesAddressBookWithoutAccount() {
        // Create address book account without corresponding account
        assertTrue(accountManager.addAccountExplicitly(addressBookAccount, null, null))
        assertEquals(listOf(addressBookAccount), accountManager.getAccountsByType(addressBookAccountType).toList())

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
        TestAccount.provide { existingAccount ->
            // Create address book account _with_ corresponding account and verify
            val userData = Bundle(2).apply {
                putString(LocalAddressBook.USER_DATA_ACCOUNT_NAME, existingAccount.name)
                putString(LocalAddressBook.USER_DATA_ACCOUNT_TYPE, existingAccount.type)
            }
            assertTrue(accountManager.addAccountExplicitly(addressBookAccount, null, userData))
            assertEquals(listOf(addressBookAccount), accountManager.getAccountsByType(addressBookAccountType).toList())

            // Create worker and run the method
            val worker = TestListenableWorkerBuilder<AccountsCleanupWorker>(context)
                .setWorkerFactory(workerFactory)
                .build()
            worker.cleanUpAddressBooks()

            // Verify account was _not_ deleted
            assertEquals(listOf(addressBookAccount), accountManager.getAccountsByType(addressBookAccountType).toList())
        }
    }


    // helpers

    private fun createTestService(): Service {
        val service = Service(id=0, accountName="test", type=Service.TYPE_CARDDAV, principal = null)
        val serviceId = db.serviceDao().insertOrReplace(service)
        return db.serviceDao().get(serviceId)!!
    }

}