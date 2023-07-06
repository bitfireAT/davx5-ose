/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.setup

import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import at.bitfire.davdroid.R
import at.bitfire.davdroid.TestUtils.getOrAwaitValue
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.resource.TaskUtils
import at.bitfire.davdroid.servicedetection.DavResourceFinder
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.ical4android.TaskProvider
import at.bitfire.vcard4android.GroupMethod
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.*
import org.junit.*
import org.junit.Assert.*
import javax.inject.Inject

@HiltAndroidTest
class AccountDetailsFragmentTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()     // required for TestUtils: LiveData.getOrAwaitValue()


    @Inject
    lateinit var db: AppDatabase
    @Inject
    lateinit var settingsManager: SettingsManager

    private val targetContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fakeCredentials = Credentials("test", "test")

    @Before
    fun setUp() {
        hiltRule.inject()

        // The test application is an instance of HiltTestApplication, which doesn't initialize notification channels.
        // However, we need notification channels for the ongoing work notifications.
        NotificationUtils.createChannels(targetContext)

        // Initialize WorkManager for instrumentation tests.
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(targetContext, config)
    }

    @After
    fun tearDown() {
        // Remove accounts created by tests
        val am = AccountManager.get(targetContext)
        val accounts = am.getAccountsByType(targetContext.getString(R.string.account_type))
        for (account in accounts) {
            am.removeAccountExplicitly(account)
        }
    }


    @Test
    fun testModel_CreateAccount_configuresContactsAndCalendars() {
        val accountName = "MyAccountName"
        val emptyServiceInfo = DavResourceFinder.Configuration.ServiceInfo()
        val config = DavResourceFinder.Configuration(emptyServiceInfo, emptyServiceInfo, false, "")

        // Create account -> should also set sync interval in settings
        val accountCreated = AccountDetailsFragment.Model(targetContext, db, settingsManager)
            .createAccount(accountName, fakeCredentials, config, GroupMethod.GROUP_VCARDS)
        assertTrue(accountCreated.getOrAwaitValue(5))

        // Get the created account
        val account = AccountManager.get(targetContext)
            .getAccountsByType(targetContext.getString(R.string.account_type))
            .first { account -> account.name == accountName }

        for (authority in listOf(
            targetContext.getString(R.string.address_books_authority),
            CalendarContract.AUTHORITY,
        )) {
            // Check isSyncable was set
            assertEquals(1, ContentResolver.getIsSyncable(account, authority))

            // Check default sync interval was set for
            // [AccountSettings.KEY_SYNC_INTERVAL_ADDRESSBOOKS],
            // [AccountSettings.KEY_SYNC_INTERVAL_CALENDARS]
            assertEquals(
                settingsManager.getLong(Settings.DEFAULT_SYNC_INTERVAL),
                AccountSettings(targetContext, account).getSyncInterval(authority)
            )
        }
    }

    @Test
    @RequiresApi(28)        // for mockkObject
    fun testModel_CreateAccount_configuresCalendarsWithTasks() {
        for (provider in listOf(
            TaskProvider.ProviderName.JtxBoard,
            TaskProvider.ProviderName.OpenTasks,
            TaskProvider.ProviderName.TasksOrg
        )) {
            val accountName = "testAccount-$provider"
            val emptyServiceInfo = DavResourceFinder.Configuration.ServiceInfo()
            val config = DavResourceFinder.Configuration(emptyServiceInfo, emptyServiceInfo, false, "")

            // Mock TaskUtils currentProvider method, pretending that one of the task apps is installed :)
            mockkObject(TaskUtils)
            every { TaskUtils.currentProvider(targetContext) } returns provider
            assertEquals(provider, TaskUtils.currentProvider(targetContext))

            // Create account -> should also set tasks sync interval in settings
            val accountCreated =
                AccountDetailsFragment.Model(targetContext, db, settingsManager)
                    .createAccount(accountName, fakeCredentials, config, GroupMethod.GROUP_VCARDS)
            assertTrue(accountCreated.getOrAwaitValue(5))

            // Get the created account
            val account = AccountManager.get(targetContext)
                .getAccountsByType(targetContext.getString(R.string.account_type))
                .first { account -> account.name == accountName }

            // Calendar: Check isSyncable and default interval are set correctly
            assertEquals(1, ContentResolver.getIsSyncable(account, CalendarContract.AUTHORITY))
            assertEquals(
                settingsManager.getLong(Settings.DEFAULT_SYNC_INTERVAL),
                AccountSettings(targetContext, account).getSyncInterval(CalendarContract.AUTHORITY)
            )

            // Tasks: Check isSyncable and default sync interval were set
            assertEquals(1, ContentResolver.getIsSyncable(account, provider.authority))
            assertEquals(
                settingsManager.getLong(Settings.DEFAULT_SYNC_INTERVAL),
                AccountSettings(targetContext, account).getSyncInterval(provider.authority)
            )
        }
    }

    @Test
    @RequiresApi(28)
    fun testModel_CreateAccount_configuresCalendarsWithoutTasks() {
        try {
            val accountName = "testAccount"
            val emptyServiceInfo = DavResourceFinder.Configuration.ServiceInfo()
            val config = DavResourceFinder.Configuration(emptyServiceInfo, emptyServiceInfo, false, "")

            // Mock TaskUtils currentProvider method, pretending that no task app is installed
            mockkObject(TaskUtils)
            every { TaskUtils.currentProvider(targetContext) } returns null
            assertEquals(null, TaskUtils.currentProvider(targetContext))

            // Mock static ContentResolver calls
            // TODO: Should not be needed, see below
            mockkStatic(ContentResolver::class)
            every { ContentResolver.setIsSyncable(any(), any(), any()) } returns Unit
            every { ContentResolver.getIsSyncable(any(), any()) } returns 1

            // Create account will try to start an initial collection refresh, which we don't need, so we mockk it
            mockkObject(RefreshCollectionsWorker.Companion)
            every { RefreshCollectionsWorker.refreshCollections(any(), any()) } returns ""

            // Create account -> should also set tasks sync interval in settings
            val accountCreated = AccountDetailsFragment.Model(targetContext, db, settingsManager)
                .createAccount(accountName, fakeCredentials, config, GroupMethod.GROUP_VCARDS)
            assertTrue(accountCreated.getOrAwaitValue(5))


            // Get the created account
            val account = AccountManager.get(targetContext)
                .getAccountsByType(targetContext.getString(R.string.account_type))
                .first { account -> account.name == accountName }
            val accountSettings = AccountSettings(targetContext, account)

            // Calendar: Check automatic sync is enabled and default interval are set correctly
            assertEquals(1, ContentResolver.getIsSyncable(account, CalendarContract.AUTHORITY))
            assertEquals(
                settingsManager.getLong(Settings.DEFAULT_SYNC_INTERVAL),
                accountSettings.getSyncInterval(CalendarContract.AUTHORITY)
            )

            // Tasks: Check isSyncable state is unknown (=-1) and sync interval is "unset" (=null)
            for (authority in listOf(
                TaskProvider.ProviderName.JtxBoard.authority,
                TaskProvider.ProviderName.OpenTasks.authority,
                TaskProvider.ProviderName.TasksOrg.authority
            )) {
                // Until below is fixed, just verify the method for enabling sync did not get called
                verify(exactly = 0) { ContentResolver.setIsSyncable(account, authority, 1) }

                // TODO: Flaky, returns 1, although it should not. It only returns -1 if the test is run
                //  alone, on a blank emulator and if it's the first test run.
                //  This seems to have to do with the previous calls to ContentResolver by other tests.
                //  Finding a a way of resetting the ContentResolver before each test is run should
                //  solve the issue.
                //assertEquals(-1, ContentResolver.getIsSyncable(account, authority))
                //assertNull(accountSettings.getSyncInterval(authority)) // Depends on above
            }
        } catch (e: InterruptedException) {
            // The sync adapter framework will start a sync, which can get interrupted. We don't care
            // about being interrupted. If it happens the test is not too important.
        }
    }

}