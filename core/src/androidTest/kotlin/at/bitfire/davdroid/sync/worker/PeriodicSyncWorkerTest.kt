/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.worker

import android.accounts.Account
import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import at.bitfire.davdroid.R
import at.bitfire.davdroid.accounts.LegacyAccount
import at.bitfire.davdroid.di.SupportLibsModule
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.sync.account.TestAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

@HiltAndroidTest
@UninstallModules(SupportLibsModule::class)
class PeriodicSyncWorkerTest {

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var syncWorkerFactory: PeriodicSyncWorker.Factory

    @BindValue @MockK(relaxed = true)
    lateinit var workManager: WorkManager

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val mockkRule = MockKRule(this)

    lateinit var account: Account

    @Before
    fun setUp() {
        hiltRule.inject()

        account = TestAccount.create()
    }

    @After
    fun tearDown() {
        TestAccount.remove(account)
    }


    @Test
    fun doWork_cancelsItselfOnInvalidAccount() = runTest {
        val invalidAccount = Account("invalid", context.getString(R.string.account_type))

        // Run PeriodicSyncWorker as TestWorker
        val inputData = Data.Builder()
            .putString(BaseSyncWorker.INPUT_DATA_TYPE, SyncDataType.EVENTS.toString())
            .putAccountId(LegacyAccount(invalidAccount))
            .build()

        // run test worker, expect failure
        val testWorker = TestListenableWorkerBuilder<PeriodicSyncWorker>(context, inputData)
            .setWorkerFactory(object: WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                    syncWorkerFactory.create(appContext, workerParameters)
            })
            .build()
        val result = testWorker.doWork()
        assertTrue(result is ListenableWorker.Result.Failure)

        // verify that worker called WorkManager.cancelWorkById(<its ID>)
        verify {
            workManager.cancelWorkById(testWorker.id)
        }
    }

}