/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import at.bitfire.davdroid.R
import at.bitfire.davdroid.TestUtils.workScheduledOrRunning
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.ui.NotificationUtils
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.junit4.MockKRule
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class PeriodicSyncWorkerTest {

    companion object {

        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

        private val accountManager = AccountManager.get(context)
        private val account = Account("Test Account", context.getString(R.string.account_type))
        private val fakeCredentials = Credentials("test", "test")

        @BeforeClass
        @JvmStatic
        fun setUp() {
            // The test application is an instance of HiltTestApplication, which doesn't initialize notification channels.
            // However, we need notification channels for the ongoing work notifications.
            NotificationUtils.createChannels(context)

            // Initialize WorkManager for instrumentation tests.
            val config = Configuration.Builder()
                .setMinimumLoggingLevel(Log.DEBUG)
                .build()
            WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

            assertTrue(AccountUtils.createAccount(context, account, AccountSettings.initialUserData(fakeCredentials)))
            ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1)
            ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1)
        }

        @AfterClass
        @JvmStatic
        fun removeAccount() {
            accountManager.removeAccountExplicitly(account)
        }

    }

    @get:Rule
    val hiltRule = HiltAndroidRule(this)
    @get:Rule
    val mockkRule = MockKRule(this)

    @Before
    fun inject() {
        hiltRule.inject()
    }


    @Test
    fun enable_enqueuesPeriodicWorker() {
        PeriodicSyncWorker.enable(context, account, CalendarContract.AUTHORITY, 60, false)
        val workerName = PeriodicSyncWorker.workerName(account, CalendarContract.AUTHORITY)
        assertTrue(workScheduledOrRunning(context, workerName))
    }

    @Test
    fun disable_removesPeriodicWorker() {
        PeriodicSyncWorker.enable(context, account, CalendarContract.AUTHORITY, 60, false)
        PeriodicSyncWorker.disable(context, account, CalendarContract.AUTHORITY)
        val workerName = PeriodicSyncWorker.workerName(account, CalendarContract.AUTHORITY)
        assertFalse(workScheduledOrRunning(context, workerName))
    }

    @Test
    fun doWork_cancelsItselfOnInvalidAccount() {
        val invalidAccount = Account("invalid", context.getString(R.string.account_type))

        // Run PeriodicSyncWorker as TestWorker
        val inputData = workDataOf(
            BaseSyncWorker.ARG_AUTHORITY to CalendarContract.AUTHORITY,
            BaseSyncWorker.ARG_ACCOUNT_NAME to invalidAccount.name,
            BaseSyncWorker.ARG_ACCOUNT_TYPE to invalidAccount.type
        )

        // mock WorkManager to observe cancellation call
        val workManager = WorkManager.getInstance(context)
        mockkObject(workManager)

        // run test worker, expect failure
        val testWorker = TestListenableWorkerBuilder<PeriodicSyncWorker>(context, inputData).build()
        val result = runBlocking {
            testWorker.doWork()
        }
        assertTrue(result is ListenableWorker.Result.Failure)

        // verify that worker called WorkManager.cancelWorkById(<its ID>)
        verify {
            workManager.cancelWorkById(testWorker.id)
        }
    }

}