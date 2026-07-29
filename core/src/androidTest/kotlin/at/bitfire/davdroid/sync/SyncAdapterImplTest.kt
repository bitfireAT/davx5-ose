/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import android.provider.CalendarContract
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.WorkInfo
import androidx.work.WorkManager
import at.bitfire.davdroid.TestUtils
import at.bitfire.davdroid.sync.account.TestAccount
import at.bitfire.davdroid.sync.adapter.SyncAdapterImpl
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import com.google.common.util.concurrent.Futures
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds

@HiltAndroidTest
class SyncAdapterImplTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockkRule = MockKRule(this)

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var syncAdapterImplProvider: Provider<SyncAdapterImpl>

    @BindValue @MockK
    lateinit var syncWorkerManager: SyncWorkerManager

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private val globalSyncStateBeforeTest = ContentResolver.getMasterSyncAutomatically()

    lateinit var account: Account
    private lateinit var syncAdapter: SyncAdapterImpl
    private lateinit var workManager: WorkManager

    private val mockSyncWorkerName = "TheSyncWorker"

    @Before
    fun setUp() {
        hiltRule.inject()
        TestUtils.setUpWorkManager(context, workerFactory)

        account = TestAccount.create()
        syncAdapter = spyk(syncAdapterImplProvider.get())
        workManager = WorkManager.getInstance(context)

        ContentResolver.setMasterSyncAutomatically(true)
        ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, true)
        ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1)

        // don't actually create a worker
        coEvery {
            syncWorkerManager.enqueueOneTime(account = any(), dataType = any(), fromUpload = any())
        } returns mockSyncWorkerName
    }

    @After
    fun tearDown() {
        ContentResolver.setMasterSyncAutomatically(globalSyncStateBeforeTest)
        TestAccount.remove(account)
    }

    /**
     * Stubs [workManager] so that [SyncAdapterImpl]'s `waitForWorker()` finds a not-yet-finished
     * "TheSyncWorker", and then waits on [finishesWith] to learn when it's done.
     *
     * @return the worker's id (needed to `verify` that [WorkManager.getWorkInfoByIdFlow] was called for it)
     */
    private fun stubOneTimeWorker(finishesWith: Flow<WorkInfo>): UUID {
        val workId = UUID.randomUUID()
        val runningWorkInfo = mockk<WorkInfo> {
            every { id } returns workId
            every { state } returns WorkInfo.State.RUNNING
        }
        every { workManager.getWorkInfosForUniqueWork(mockSyncWorkerName) } returns
                Futures.immediateFuture(listOf(runningWorkInfo))
        every { workManager.getWorkInfoByIdFlow(workId) } returns finishesWith
        return workId
    }

    @Test
    fun testSyncAdapter_onPerformSync_cancellation() {
        mockkObject(workManager) {
            // assume worker takes a long time to finish
            stubOneTimeWorker(flow { awaitCancellation() })

            // run on a thread like the sync framework calls does
            val sync = thread {
                syncAdapter.onPerformSync(account, Bundle(), CalendarContract.AUTHORITY, mockk(), SyncResult())
            }

            // wait until performSync has started (so that waitScope is guaranteed to be set before we cancel it below)
            coVerify(timeout = 5000) { syncAdapter.performSync(any(), any(), any()) }

            // simulate incoming cancellation from sync framework
            syncAdapter.onSyncCanceled()

            // wait for sync to finish (should happen immediately)
            sync.join(5000)
            assertFalse("Sync thread was not terminated on cancellation", sync.isAlive)
        }
    }

    @Test
    fun testSyncAdapter_onPerformSync_returnsAfterTimeout() {
        mockkObject(workManager) {
            // assume worker takes a long time to finish
            stubOneTimeWorker(flow { awaitCancellation() })

            // don't really wait 10 minutes for the timeout to happen
            syncAdapter.workerWaitTimeout = 100.milliseconds

            // should terminate after 100 ms
            val sync = thread {
                syncAdapter.onPerformSync(account, Bundle(), CalendarContract.AUTHORITY, mockk(), SyncResult())
            }
            sync.join(5000)
            assertFalse("Sync thread was not terminated after timeout", sync.isAlive)
        }
    }

    @Test
    fun testSyncAdapter_onPerformSync_runsInTime() {
        mockkObject(workManager) {
            // assume worker is enqueued, then immediately finishes with success
            val succeededWorkInfo = mockk<WorkInfo> {
                every { state } returns WorkInfo.State.SUCCEEDED
            }
            stubOneTimeWorker(flowOf(succeededWorkInfo))

            // should just run
            val sync = thread {
                syncAdapter.onPerformSync(account, Bundle(), CalendarContract.AUTHORITY, mockk(), SyncResult())
            }
            sync.join(1000)
            assertFalse(sync.isAlive)
        }
    }

}
