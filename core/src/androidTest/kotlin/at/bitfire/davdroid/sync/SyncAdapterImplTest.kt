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
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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

    lateinit var account: Account

    private var masterSyncStateBeforeTest = ContentResolver.getMasterSyncAutomatically()

    @Before
    fun setUp() {
        hiltRule.inject()
        TestUtils.setUpWorkManager(context, workerFactory)

        account = TestAccount.create()

        ContentResolver.setMasterSyncAutomatically(true)
        ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, true)
        ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1)
    }

    @After
    fun tearDown() {
        ContentResolver.setMasterSyncAutomatically(masterSyncStateBeforeTest)
        TestAccount.remove(account)
    }


    @Test
    fun testSyncAdapter_onPerformSync_cancellation() = runTest {
        val workManager = WorkManager.getInstance(context)
        val syncAdapter = syncAdapterImplProvider.get()

        mockkObject(workManager) {
            // don't actually create a worker
            coEvery { syncWorkerManager.enqueueOneTime(any(), any()) } returns "TheSyncWorker"

            // assume worker is still running
            val workId = UUID.randomUUID()
            val runningWorkInfo = mockk<WorkInfo> {
                every { id } returns workId
                every { state } returns WorkInfo.State.RUNNING
            }
            every { workManager.getWorkInfosForUniqueWork("TheSyncWorker") } returns
                    Futures.immediateFuture(listOf(runningWorkInfo))
            // assume worker takes a long time to finish
            every { workManager.getWorkInfoByIdFlow(workId) } returns flow { awaitCancellation() }

            val sync = launch {
                syncAdapter.onPerformSync(account, Bundle(), CalendarContract.AUTHORITY, mockk(), SyncResult())
            }

            // simulate incoming cancellation from sync framework
            syncAdapter.onSyncCanceled()

            // wait for sync to finish (should happen immediately)
            sync.join()

            // verify that the sync adapter actually looked up the (still running) worker
            verify { workManager.getWorkInfosForUniqueWork("TheSyncWorker") }
        }
    }

    @Test
    fun testSyncAdapter_onPerformSync_clearsPendingFlag() = runBlocking {
        val syncAdapter = syncAdapterImplProvider.get()

        // Don't actually create a worker
        coEvery { syncWorkerManager.enqueueOneTime(any(), any()) } returns "TheSyncWorker"

        // Make the real sync framework genuinely mark this account/authority as pending.
        val extras = Bundle()
        ContentResolver.requestSync(account, CalendarContract.AUTHORITY, extras)
        withTimeout(10.seconds) {
            while (!ContentResolver.isSyncPending(account, CalendarContract.AUTHORITY))
                delay(100.milliseconds)
        }

        // Call SyncAdapterImpl directly, just like the sync framework would -
        // this must clear the pending flag again.
        syncAdapter.onPerformSync(account, extras, CalendarContract.AUTHORITY, mockk(), SyncResult())

        // Verify that pending flag is cleared
        withTimeout(10.seconds) {
            while (ContentResolver.isSyncPending(account, CalendarContract.AUTHORITY))
                delay(100.milliseconds)
        }
    }

    @Test
    fun testSyncAdapter_onPerformSync_returnsAfterTimeout() {
        val workManager = WorkManager.getInstance(context)
        val syncAdapter = syncAdapterImplProvider.get()

        mockkObject(workManager) {
            // don't actually create a worker
            coEvery { syncWorkerManager.enqueueOneTime(any(), any()) } returns "TheSyncWorker"

            // assume worker is still running
            val workId = UUID.randomUUID()
            val runningWorkInfo = mockk<WorkInfo> {
                every { id } returns workId
                every { state } returns WorkInfo.State.RUNNING
            }
            every { workManager.getWorkInfosForUniqueWork("TheSyncWorker") } returns
                    Futures.immediateFuture(listOf(runningWorkInfo))
            // assume worker takes a long time to finish
            every { workManager.getWorkInfoByIdFlow(workId) } returns flow { awaitCancellation() }

            mockkStatic("kotlinx.coroutines.TimeoutKt") {   // mock global extension function
                // immediate timeout (instead of really waiting)
                coEvery {
                    withTimeout(
                        any<Long>(),
                        any<suspend CoroutineScope.() -> Unit>()
                    )
                } throws CancellationException("Simulated timeout")

                syncAdapter.onPerformSync(account, Bundle(), CalendarContract.AUTHORITY, mockk(), SyncResult())

                // verify that the sync adapter actually looked up the (still running) worker
                verify { workManager.getWorkInfosForUniqueWork("TheSyncWorker") }
            }
        }
    }

    @Test
    fun testSyncAdapter_onPerformSync_runsInTime() {
        val workManager = WorkManager.getInstance(context)
        val syncAdapter = syncAdapterImplProvider.get()

        mockkObject(workManager) {
            // don't actually create a worker
            coEvery { syncWorkerManager.enqueueOneTime(any(), any()) } returns "TheSyncWorker"

            // assume worker is enqueued, then immediately finishes with success
            val workId = UUID.randomUUID()
            val enqueuedWorkInfo = mockk<WorkInfo> {
                every { id } returns workId
                every { state } returns WorkInfo.State.ENQUEUED
            }
            val succeededWorkInfo = mockk<WorkInfo> {
                every { id } returns workId
                every { state } returns WorkInfo.State.SUCCEEDED
            }
            every { workManager.getWorkInfosForUniqueWork("TheSyncWorker") } returns
                    Futures.immediateFuture(listOf(enqueuedWorkInfo))
            every { workManager.getWorkInfoByIdFlow(workId) } returns flowOf(succeededWorkInfo)

            // should just run
            syncAdapter.onPerformSync(account, Bundle(), CalendarContract.AUTHORITY, mockk(), SyncResult())

            // verify that the sync adapter actually waited for the worker to finish
            verify { workManager.getWorkInfoByIdFlow(workId) }
        }
    }

}
