/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.account

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.testing.TestListenableWorkerBuilder
import at.bitfire.davdroid.R
import at.bitfire.davdroid.TestUtils
import at.bitfire.davdroid.accounts.AndroidAccountManager
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.settings.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertFalse
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
    lateinit var accountManager: AccountManager

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

    lateinit var dbAndroidAccount: Account
    lateinit var service: Service

    @Before
    fun setUp() {
        hiltRule.inject()
        TestUtils.setUpWorkManager(context, workerFactory)

        service = createTestService()

        dbAndroidAccount = Account("Fancy DB account", context.getString(R.string.account_type_db))
    }

    @After
    fun tearDown() {
        // Remove the account here in any case; Nice to have when the test fails
        db.dbAccountDao().deleteAllBlocking()
        accountManager.removeAccountExplicitly(dbAndroidAccount)
    }


    @Test
    fun testDoWork_keepsOrphanedDbAndroidAccount() {
        // Android account linked to a DB account that doesn't exist; may have unsynced data, so only the user may delete it
        assertTrue(accountManager.addAccountExplicitly(dbAndroidAccount, null, null))
        accountManager.setUserData(dbAndroidAccount, AndroidAccountManager.USER_DATA_ACCOUNT_ID, "999")

        val worker = TestListenableWorkerBuilder<AccountsCleanupWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()
        worker.doWork()

        assertTrue(accountManager.accounts.contains(dbAndroidAccount))
    }


    @Test
    fun testCleanUpServices_noAccount() {
        // Insert service that reference to invalid account
        db.serviceDao()
            .insertOrReplaceBlocking(
                Service(
                    id = 1,
                    accountName = "test",
                    type = Service.TYPE_CALDAV,
                    principal = null
                )
            )
        assertNotNull(db.serviceDao().getBlocking(1))

        // Create worker and run the method
        val worker = TestListenableWorkerBuilder<AccountsCleanupWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()
        worker.cleanUpServices()

        // Verify that service is deleted
        assertNull(db.serviceDao().getBlocking(1))
    }

    @Test
    fun testCleanUpServices_oneAccount() {
        TestAccount.provide { existingAccount ->
            // Insert services, one that reference the existing account and one that references an invalid account
            db.serviceDao()
                .insertOrReplaceBlocking(
                    Service(
                        id = 1,
                        accountName = existingAccount.name,
                        type = Service.TYPE_CALDAV,
                        principal = null
                    )
                )
            assertNotNull(db.serviceDao().getBlocking(1))

            db.serviceDao()
                .insertOrReplaceBlocking(
                    Service(
                        id = 2,
                        accountName = "not existing",
                        type = Service.TYPE_CARDDAV,
                        principal = null
                    )
                )
            assertNotNull(db.serviceDao().getBlocking(2))

            // Create worker and run the method
            val worker = TestListenableWorkerBuilder<AccountsCleanupWorker>(context)
                .setWorkerFactory(workerFactory)
                .build()
            worker.cleanUpServices()

            // Verify that one service is deleted and the other one is kept
            assertNotNull(db.serviceDao().getBlocking(1))
            assertNull(db.serviceDao().getBlocking(2))
        }
    }


    // helpers

    private fun createTestService(): Service {
        val service = Service(id=0, accountName="test", type=Service.TYPE_CARDDAV, principal = null)
        val serviceId = db.serviceDao().insertOrReplaceBlocking(service)
        return db.serviceDao().getBlocking(serviceId)!!
    }

}