/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.worker

import android.accounts.Account
import android.content.Context
import android.provider.CalendarContract
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import at.bitfire.davdroid.TestUtils
import at.bitfire.davdroid.TestUtils.workScheduledOrRunning
import at.bitfire.davdroid.sync.account.TestAccountAuthenticator
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class SyncWorkerManagerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var syncWorkerManager: SyncWorkerManager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    lateinit var account: Account

    @Before
    fun setUp() {
        hiltRule.inject()

        // Initialize WorkManager for instrumentation tests.
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setWorkerFactory(workerFactory)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        account = TestAccountAuthenticator.create()
    }

    @After
    fun tearDown() {
        TestAccountAuthenticator.remove(account)
    }


    // one-time sync workers

    @Test
    fun testEnqueueOneTime() {
        val workerName = OneTimeSyncWorker.workerName(account, CalendarContract.AUTHORITY)
        assertFalse(TestUtils.workScheduledOrRunningOrSuccessful(context, workerName))

        val returnedName = syncWorkerManager.enqueueOneTime(account, CalendarContract.AUTHORITY)
        assertEquals(workerName, returnedName)
        assertTrue(TestUtils.workScheduledOrRunningOrSuccessful(context, workerName))
    }


    // periodic sync workers

    @Test
    fun enablePeriodic() {
        syncWorkerManager.enablePeriodic(account, CalendarContract.AUTHORITY, 60, false).result.get()

        val workerName = PeriodicSyncWorker.workerName(account, CalendarContract.AUTHORITY)
        assertTrue(workScheduledOrRunning(context, workerName))
    }

    @Test
    fun disablePeriodic() {
        syncWorkerManager.enablePeriodic(account, CalendarContract.AUTHORITY, 60, false).result.get()
        syncWorkerManager.disablePeriodic(account, CalendarContract.AUTHORITY).result.get()

        val workerName = PeriodicSyncWorker.workerName(account, CalendarContract.AUTHORITY)
        assertFalse(workScheduledOrRunning(context, workerName))
    }

}