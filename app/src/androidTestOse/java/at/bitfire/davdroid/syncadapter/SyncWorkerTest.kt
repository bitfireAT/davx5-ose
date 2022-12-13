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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.ui.NotificationUtils
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@HiltAndroidTest
class SyncWorkerTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private val accountManager = AccountManager.get(context)
    private val account = Account("Test Account", context.getString(R.string.account_type))
    private val fakeCredentials = Credentials("test", "test")

    @Before
    fun setUp() {
        hiltRule.inject()

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
    fun testRequestSync_enqueuesWorker() {
        SyncWorker.requestSync(context, account, CalendarContract.AUTHORITY)
        val workerName = SyncWorker.workerName(account, CalendarContract.AUTHORITY)
        assertTrue(workScheduledOrRunning(workerName))
    }

    @Test
    fun testStopSync_stopsWorker() {
        SyncWorker.requestSync(context, account, CalendarContract.AUTHORITY)
        SyncWorker.stopSync(context, account, CalendarContract.AUTHORITY)
        val workerName = SyncWorker.workerName(account, CalendarContract.AUTHORITY)
        assertFalse(workScheduledOrRunning(workerName))

        // here we could test whether stopping the work really interrupts the sync thread
    }

    private fun workScheduledOrRunning(workerName: String): Boolean {
        val future: ListenableFuture<List<WorkInfo>> = WorkManager.getInstance(context).getWorkInfosForUniqueWork(workerName)
        val workInfoList: List<WorkInfo>
        try {
            workInfoList = future.get()
        } catch (e: Exception) {
            Logger.log.severe("Failed to retrieve work info list for worker $workerName", )
            return false
        }
        for (workInfo in workInfoList) {
            val state = workInfo.state
            if (state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED)
                return true
        }
        return false
    }

}