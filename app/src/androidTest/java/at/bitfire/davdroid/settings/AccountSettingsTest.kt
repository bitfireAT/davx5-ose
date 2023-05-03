/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.settings

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import at.bitfire.davdroid.R
import at.bitfire.davdroid.TestUtils
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.syncadapter.AccountUtils
import at.bitfire.davdroid.syncadapter.PeriodicSyncWorker
import at.bitfire.davdroid.syncadapter.SyncManagerTest
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.ical4android.TaskProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidTest
class AccountSettingsTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @Inject
    lateinit var settingsManager: SettingsManager


    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    val account = Account(javaClass.canonicalName, SyncManagerTest.context.getString(R.string.account_type))
    val fakeCredentials = Credentials("test", "test")

    val authorities = listOf(
        context.getString(R.string.address_books_authority),
        CalendarContract.AUTHORITY,
        TaskProvider.ProviderName.JtxBoard.authority,
        TaskProvider.ProviderName.OpenTasks.authority,
        TaskProvider.ProviderName.TasksOrg.authority
    )

    @Before
    fun setUp() {
        hiltRule.inject()

        assertTrue(AccountUtils.createAccount(
            context,
            account,
            AccountSettings.initialUserData(fakeCredentials)
        ))
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
        val futureResult = AccountManager.get(context).removeAccount(account, {}, null)
        assertTrue(futureResult.getResult(10, TimeUnit.SECONDS))
    }


    @Test
    fun testSyncIntervals() {
        val settings = AccountSettings(context, account)
        val presetIntervals =
            context.resources.getStringArray(R.array.settings_sync_interval_seconds)
                .map { it.toLong() }
                .filter { it != AccountSettings.SYNC_INTERVAL_MANUALLY }
        for (interval in presetIntervals) {
            assertTrue(settings.setSyncInterval(CalendarContract.AUTHORITY, interval))
            assertEquals(interval, settings.getSyncInterval(CalendarContract.AUTHORITY))
        }
    }

    @Test
    fun testSyncIntervals_Syncable() {
        val settings = AccountSettings(context, account)
        val interval = 15*60L    // 15 min
        val result = settings.setSyncInterval(CalendarContract.AUTHORITY, interval)
        assertTrue(result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testSyncIntervals_TooShort() {
        val settings = AccountSettings(context, account)
        val interval = 60L      // 1 min is not supported by Android
        settings.setSyncInterval(CalendarContract.AUTHORITY, interval)
    }

    @Test
    fun testSyncIntervals_activatesPeriodicSyncWorker() {
        val settings = AccountSettings(context, account)
        val interval = 15*60L
        for (authority in authorities) {
            ContentResolver.setIsSyncable(account, authority, 1)
            assertTrue(settings.setSyncInterval(authority, interval))
            assertTrue(TestUtils.workScheduledOrRunningOrSuccessful(context, PeriodicSyncWorker.workerName(account, authority)))
            assertEquals(interval, settings.getSyncInterval(authority))
        }
    }

    @Test
    fun testSyncIntervals_disablesPeriodicSyncWorker() {
        val settings = AccountSettings(context, account)
        val interval = AccountSettings.SYNC_INTERVAL_MANUALLY // -1
        for (authority in authorities) {
            ContentResolver.setIsSyncable(account, authority, 1)
            assertTrue(settings.setSyncInterval(authority, interval))
            assertFalse(TestUtils.workScheduledOrRunningOrSuccessful(context, PeriodicSyncWorker.workerName(account, authority)))
            assertEquals(AccountSettings.SYNC_INTERVAL_MANUALLY, settings.getSyncInterval(authority))
        }
    }
}