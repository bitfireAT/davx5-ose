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
import io.ktor.client.plugins.auth.providers.DigestAuthCredentials
import io.ktor.client.plugins.auth.providers.DigestAuthProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
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

    @Test
    @OptIn(DelicateCoroutinesApi::class)
    fun testStillRequired() {
        // tests whether https://youtrack.jetbrains.com/issue/KTOR-9722 is still present

        // congest default dispatcher
        val processors = Runtime.getRuntime().availableProcessors()
        val allCongested = CountDownLatch(processors)
        val scope = CoroutineScope(Dispatchers.Default)
        repeat(processors) {
            scope.launch {
                allCongested.countDown()
                runInterruptible {
                    Thread.sleep(10_000)
                }
            }
        }
        allCongested.await()

        // now construct DigestAuthProvider and verify deadlock
        val thread = Thread {
            try {
                DigestAuthProvider(credentials = { DigestAuthCredentials("user", "pass") })
            } catch (_: InterruptedException) {
                // thrown when thread.interrupt() is called below
            }
        }
        try {
            thread.start()
            thread.join(1_000)
            assertTrue("KTOR issue 9722 not present anymore, remove IoCoroutineWorker + tests", thread.isAlive)
        } finally {
            thread.interrupt()
            scope.cancel()
        }
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
