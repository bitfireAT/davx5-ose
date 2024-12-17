/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.sync.worker

import android.accounts.Account
import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.sync.account.TestAccountAuthenticator
import at.bitfire.davdroid.test.R
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
class PeriodicSyncWorkerTest {

    @Inject
    @ApplicationContext
    lateinit var context: Context
    val testContext = InstrumentationRegistry.getInstrumentation().context

    @Inject
    lateinit var syncWorkerFactory: PeriodicSyncWorker.Factory

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    lateinit var account: Account

    @Before
    fun setUp() {
        hiltRule.inject()

        // Initialize WorkManager for instrumentation tests.
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        account = TestAccountAuthenticator.create()
    }

    @After
    fun tearDown() {
        TestAccountAuthenticator.remove(account)
    }


    @Test
    fun doWork_cancelsItselfOnInvalidAccount() {
        val invalidAccount = Account("invalid", testContext.getString(R.string.account_type_test))

        // Run PeriodicSyncWorker as TestWorker
        val inputData = workDataOf(
            BaseSyncWorker.INPUT_DATA_TYPE to SyncDataType.EVENTS.toString(),
            BaseSyncWorker.INPUT_ACCOUNT_NAME to invalidAccount.name,
            BaseSyncWorker.INPUT_ACCOUNT_TYPE to invalidAccount.type
        )

        // mock WorkManager to observe cancellation call
        val workManager = WorkManager.getInstance(context)
        mockkObject(workManager)

        // run test worker, expect failure
        val testWorker = TestListenableWorkerBuilder<PeriodicSyncWorker>(context, inputData)
            .setWorkerFactory(object: WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                    syncWorkerFactory.create(appContext, workerParameters)
            })
            .build()
        val result = runBlocking {
            testWorker.doWork()
        }
        assertTrue(result is ListenableWorker.Result.Failure)

        // verify that worker called WorkManager.cancelWorkById(<its ID>)
        verify {
            workManager.cancelWorkById(testWorker.id)
        }
    }

}