/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.testing.TestWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import at.bitfire.davdroid.R
import at.bitfire.davdroid.TestUtils.workScheduledOrRunningOrSuccessful
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.network.ConnectionUtils
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.ui.NotificationUtils
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.Executors

@HiltAndroidTest
class SyncWorkerTest {

    val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val accountManager = AccountManager.get(context)
    private val account = Account("Test Account", context.getString(R.string.account_type))
    private val fakeCredentials = Credentials("test", "test")

    private val executor = Executors.newSingleThreadExecutor()

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun inject() {
        hiltRule.inject()
    }

    @Before
    fun setUp() {
        assertTrue(AccountUtils.createAccount(context, account, AccountSettings.initialUserData(fakeCredentials)))
        ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1)
        ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 0)

        // The test application is an instance of HiltTestApplication, which doesn't initialize notification channels.
        // However, we need notification channels for the ongoing work notifications.
        NotificationUtils.createChannels(context)

        // Initialize WorkManager for instrumentation tests.
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @After
    fun removeAccount() {
        accountManager.removeAccountExplicitly(account)
    }


    @Test
    fun testEnqueue_enqueuesWorker() {
        SyncWorker.enqueue(context, account, CalendarContract.AUTHORITY)
        val workerName = SyncWorker.workerName(account, CalendarContract.AUTHORITY)
        assertTrue(workScheduledOrRunningOrSuccessful(context, workerName))
    }

    @Test
    fun testWifiConditionsMet_withoutWifi() {
        val accountSettings = mockk<AccountSettings>()
        every { accountSettings.getSyncWifiOnly() } returns false
        assertTrue(SyncWorker.wifiConditionsMet(context, accountSettings))
    }
    
    @Test
    fun testWifiConditionsMet_anyWifi_wifiEnabled() {
        val accountSettings = AccountSettings(context, account)
        accountSettings.setSyncWiFiOnly(true)

        mockkObject(ConnectionUtils)
        every { ConnectionUtils.wifiAvailable(any()) } returns true
        mockkObject(SyncWorker.Companion)
        every { SyncWorker.Companion.correctWifiSsid(any(), any()) } returns true

        assertTrue(SyncWorker.wifiConditionsMet(context, accountSettings))
    }

    @Test
    fun testWifiConditionsMet_anyWifi_wifiDisabled() {
        val accountSettings = AccountSettings(context, account)
        accountSettings.setSyncWiFiOnly(true)

        mockkObject(ConnectionUtils)
        every { ConnectionUtils.wifiAvailable(any()) } returns false
        mockkObject(SyncWorker.Companion)
        every { SyncWorker.Companion.correctWifiSsid(any(), any()) } returns true

        assertFalse(SyncWorker.wifiConditionsMet(context, accountSettings))
    }


    @Test
    fun testWifiConditionsMet_correctWifiSsid() {
        // TODO: Write test
    }

    @Test
    fun testWifiConditionsMet_wrongWifiSsid() {
        // TODO: Write test
    }


    @Test
    fun testOnStopped_interruptsSyncThread() {
        val authority = CalendarContract.AUTHORITY
        val inputData = workDataOf(
            SyncWorker.ARG_AUTHORITY to authority,
            SyncWorker.ARG_ACCOUNT_NAME to account.name,
            SyncWorker.ARG_ACCOUNT_TYPE to account.type
        )

        // Create SyncWorker as TestWorker
        val testSyncWorker = TestWorkerBuilder<SyncWorker>(context, executor, inputData).build()
        assertNull(testSyncWorker.syncThread)

        // Run SyncWorker and assert sync thread is alive
        testSyncWorker.doWork()
        assertNotNull(testSyncWorker.syncThread)
        assertTrue(testSyncWorker.syncThread!!.isAlive)
        assertFalse(testSyncWorker.syncThread!!.isInterrupted) // Sync running

        // Stop SyncWorker and assert sync thread was interrupted
        testSyncWorker.onStopped()
        assertNotNull(testSyncWorker.syncThread)
        assertTrue(testSyncWorker.syncThread!!.isAlive)
        assertTrue(testSyncWorker.syncThread!!.isInterrupted) // Sync thread interrupted
    }

}