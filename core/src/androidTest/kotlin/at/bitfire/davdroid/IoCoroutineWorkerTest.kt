/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.coroutines.ContinuationInterceptor

@HiltAndroidTest
class IoCoroutineWorkerTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    val executor: ExecutorService = Executors.newSingleThreadExecutor()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        executor.shutdown()
    }

    @Test
    fun testDoWork_runsOnInjectedIoDispatcher() = runTest {
        val testIoDispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()
        val worker = TestListenableWorkerBuilder<TestWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()
            .apply {
                // override injected ioDispatcher by our custom one
                ioDispatcher = testIoDispatcher
            }

        worker.doWork()

        assertEquals(testIoDispatcher, worker.actualDispatcher)
    }


    @HiltWorker
    class TestWorker @AssistedInject constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters
    ) : IoCoroutineWorker(context, params) {

        var actualDispatcher: CoroutineDispatcher? = null

        override suspend fun doIoWork(): Result {
            actualDispatcher = currentCoroutineContext()[ContinuationInterceptor] as CoroutineDispatcher
            return Result.success()
        }

    }

}
