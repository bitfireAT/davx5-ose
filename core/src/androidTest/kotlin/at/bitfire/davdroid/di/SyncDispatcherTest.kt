/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.plugins.auth.providers.DigestAuthCredentials
import io.ktor.client.plugins.auth.providers.DigestAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch

@HiltAndroidTest
class SyncDispatcherTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun setUp() {
        hiltRule.inject()
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
                    Thread.sleep(3_000)
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
            Assert.assertTrue("KTOR issue 9722 not present anymore, default dispatcher usable again", thread.isAlive)
        } finally {
            thread.interrupt()
            scope.cancel()
        }
    }

}