/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import android.provider.CalendarContract
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.WorkInfo
import androidx.work.WorkManager
import at.bitfire.davdroid.TestUtils
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.account.TestAccount
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.Awaits
import io.mockk.coEvery
import io.mockk.every
import io.mockk.junit4.MockKRule
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@HiltAndroidTest
class SyncAdapterServicesTest {

    lateinit var account: Account

    @Inject
    lateinit var accountSettingsFactory: AccountSettings.Factory

    @Inject
    lateinit var collectionRepository: DavCollectionRepository

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var serviceRepository: DavServiceRepository

    @Inject
    lateinit var syncConditionsFactory: SyncConditions.Factory

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockkRule = MockKRule(this)

    // test methods should run quickly and not wait 60 seconds for a sync timeout or something like that
    @get:Rule
    val timeoutRule: Timeout = Timeout.seconds(5)


    @Before
    fun setUp() {
        hiltRule.inject()
        TestUtils.setUpWorkManager(context, workerFactory)

        account = TestAccount.create()
    }

    @After
    fun tearDown() {
        TestAccount.remove(account)
    }


    private fun syncAdapter(
        syncWorkerManager: SyncWorkerManager
    ): SyncAdapterService.SyncAdapter =
        SyncAdapterService.SyncAdapter(
            accountSettingsFactory = accountSettingsFactory,
            collectionRepository = collectionRepository,
            serviceRepository = serviceRepository,
            context = context,
            logger = logger,
            syncConditionsFactory = syncConditionsFactory,
            syncWorkerManager = syncWorkerManager
        )


    @Test
    fun testSyncAdapter_onPerformSync_cancellation() = runTest {
        val syncWorkerManager = mockk<SyncWorkerManager>()
        val syncAdapter = syncAdapter(syncWorkerManager = syncWorkerManager)
        val workManager = WorkManager.getInstance(context)

        mockkObject(workManager) {
            // don't actually create a worker
            every { syncWorkerManager.enqueueOneTime(any(), any()) } returns "TheSyncWorker"

            // assume worker takes a long time
            every { workManager.getWorkInfosForUniqueWorkFlow("TheSyncWorker") } just Awaits

            val sync = launch {
                syncAdapter.onPerformSync(account, Bundle(), CalendarContract.AUTHORITY, mockk(), SyncResult())
            }

            // simulate incoming cancellation from sync framework
            syncAdapter.onSyncCanceled()

            // wait for sync to finish (should happen immediately)
            sync.join()
        }
    }

    @Test
    fun testSyncAdapter_onPerformSync_returnsAfterTimeout() {
        val syncWorkerManager = mockk<SyncWorkerManager>()
        val syncAdapter = syncAdapter(syncWorkerManager = syncWorkerManager)
        val workManager = WorkManager.getInstance(context)

        mockkObject(workManager) {
            // don't actually create a worker
            every { syncWorkerManager.enqueueOneTime(any(), any()) } returns "TheSyncWorker"

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
        val syncWorkerManager = mockk<SyncWorkerManager>()
        val syncAdapter = syncAdapter(syncWorkerManager = syncWorkerManager)
        val workManager = WorkManager.getInstance(context)

        mockkObject(workManager) {
            // don't actually create a worker
            every { syncWorkerManager.enqueueOneTime(any(), any()) } returns "TheSyncWorker"

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