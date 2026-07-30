/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import at.bitfire.davdroid.TestUtils
import at.bitfire.davdroid.TestUtils.workScheduledOrRunning
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.LegacyAccount
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.sync.account.TestAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
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

    lateinit var accountId: LegacyAccount

    @Before
    fun setUp() {
        hiltRule.inject()
        TestUtils.setUpWorkManager(context, workerFactory)

        accountId = LegacyAccount(TestAccount.create())
    }

    @After
    fun tearDown() {
        TestAccount.remove(accountId.androidAccount)
    }


    // one-time sync workers

    @Test
    fun testEnqueueOneTime() = runTest {
        val workerName = OneTimeSyncWorker.workerName(accountId, SyncDataType.EVENTS)
        assertFalse(TestUtils.workScheduledOrRunningOrSuccessful(context, workerName))

        val returnedName = syncWorkerManager.enqueueOneTime(accountId, SyncDataType.EVENTS)
        assertEquals(workerName, returnedName)
        assertTrue(TestUtils.workScheduledOrRunningOrSuccessful(context, workerName))
    }


    // periodic sync workers

    @Test
    fun enablePeriodic() {
        syncWorkerManager.enablePeriodic(accountId, SyncDataType.EVENTS, 60, false).result.get()

        val workerName = PeriodicSyncWorker.workerName(accountId, SyncDataType.EVENTS)
        assertTrue(workScheduledOrRunning(context, workerName))
    }

    @Test
    fun disablePeriodic() {
        syncWorkerManager.enablePeriodic(accountId, SyncDataType.EVENTS, 60, false).result.get()
        syncWorkerManager.disablePeriodic(accountId, SyncDataType.EVENTS).result.get()

        val workerName = PeriodicSyncWorker.workerName(accountId, SyncDataType.EVENTS)
        assertFalse(workScheduledOrRunning(context, workerName))
    }

}