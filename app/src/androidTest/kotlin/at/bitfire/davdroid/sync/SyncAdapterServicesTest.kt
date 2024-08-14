/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import android.provider.CalendarContract
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import at.bitfire.davdroid.sync.account.TestAccountAuthenticator
import at.bitfire.davdroid.sync.worker.OneTimeSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.Awaits
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Provider
import kotlin.coroutines.cancellation.CancellationException

@HiltAndroidTest
class SyncAdapterServicesTest {

    lateinit var account: Account

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var syncAdapterProvider: Provider<SyncAdapterService.SyncAdapter>

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    // test methods should run quickly and not wait 60 seconds for a sync timeout or something like that
    @get:Rule
    val timeoutRule: Timeout = Timeout.seconds(5)


    @Before
    fun setUp() {
        hiltRule.inject()

        account = TestAccountAuthenticator.create()

        // Initialize WorkManager for instrumentation tests.
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setWorkerFactory(workerFactory)
            .setTaskExecutor(Executors.newSingleThreadExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @After
    fun tearDown() {
        TestAccountAuthenticator.remove(account)
    }


    @Test
    fun testSyncAdapter_onPerformSync_cancellation() {
        val syncAdapter = syncAdapterProvider.get()
        val workManager = WorkManager.getInstance(context)

        mockkObject(OneTimeSyncWorker, workManager) {
            // don't actually create a worker
            every { OneTimeSyncWorker.enqueue(any(), any(), any()) } returns "TheSyncWorker"

            // assume worker takes a long time
            every { workManager.getWorkInfosForUniqueWorkFlow("TheSyncWorker") } just Awaits

            runBlocking {
                val sync = launch {
                    syncAdapter.onPerformSync(account, Bundle(), CalendarContract.AUTHORITY, mockk(), SyncResult())
                }

                // simulate incoming cancellation from sync framework
                syncAdapter.onSyncCanceled()

                // wait for sync to finish (should happen immediately)
                sync.join()
            }
        }
    }

    @Test
    fun testSyncAdapter_onPerformSync_returnsAfterTimeout() {
        val syncAdapter = syncAdapterProvider.get()
        val workManager = WorkManager.getInstance(context)

        mockkObject(OneTimeSyncWorker, workManager) {
            // don't actually create a worker
            every { OneTimeSyncWorker.enqueue(any(), any(), any()) } returns "TheSyncWorker"

            // assume worker takes a long time
            every { workManager.getWorkInfosForUniqueWorkFlow("TheSyncWorker") } just Awaits

            mockkStatic("kotlinx.coroutines.TimeoutKt") {   // mock global extension function
                // immediate timeout (instead of really waiting)
                coEvery { withTimeout(any<Long>(), any<suspend CoroutineScope.() -> Unit>()) } throws CancellationException("Simulated timeout")

                syncAdapter.onPerformSync(account, Bundle(), CalendarContract.AUTHORITY, mockk(), SyncResult())
            }
        }
    }

    @Test
    fun testSyncAdapter_onPerformSync_runsInTime() {
        val syncAdapter = syncAdapterProvider.get()
        val workManager = WorkManager.getInstance(context)

        mockkObject(OneTimeSyncWorker, workManager) {
            // don't actually create a worker
            every { OneTimeSyncWorker.enqueue(any(), any(), any()) } returns "TheSyncWorker"

            // assume worker immediately returns with success
            val success = mockk<WorkInfo>()
            every { success.state } returns WorkInfo.State.SUCCEEDED
            every { workManager.getWorkInfosForUniqueWorkFlow("TheSyncWorker") } returns flow {
                emit(listOf(success))
                delay(60000)    // keep the flow active
            }

            // should just run
            syncAdapter.onPerformSync(account, Bundle(), CalendarContract.AUTHORITY, mockk(), SyncResult())
        }
    }

}