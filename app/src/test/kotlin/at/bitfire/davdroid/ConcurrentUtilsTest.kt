/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import at.bitfire.davdroid.util.ConcurrentUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class ConcurrentUtilsTest {

    @Test
    fun testRunSingle_DifferentKeys_Sequentially() {
        var nrCalled = AtomicInteger()
        for (i in 0 until 10)
            ConcurrentUtils.runSingle(i) { nrCalled.incrementAndGet() }
        assertEquals(10, nrCalled.get())
    }

    @Test
    fun testRunSingle_DifferentKeys_Parallel() {
        var nrCalled = AtomicInteger()
        val threads = mutableListOf<Thread>()
        for (i in 0 until 10)
            threads += thread {
                ConcurrentUtils.runSingle(i) {
                    nrCalled.incrementAndGet()
                    Thread.sleep(100)
                }
            }
        threads.forEach { it.join() }
        assertEquals(10, nrCalled.get())
    }

    @Test
    fun testRunSingle_SameKey_Sequentially() {
        val key = "a"
        var nrCalled = AtomicInteger()
        for (i in 0 until 10)
            ConcurrentUtils.runSingle(key) { nrCalled.incrementAndGet() }
        assertEquals(10, nrCalled.get())
    }

    @Test
    fun testRunSingle_SameKey_Nested() {
        val key = "a"

        var outerBlockExecuted = false
        ConcurrentUtils.runSingle(key) {
            outerBlockExecuted = true

            // Now a code block with the key is already running, further ones should be ignored
            assertFalse(ConcurrentUtils.runSingle(key) {
                fail("Shouldn't have been called")
            })
        }

        assertTrue(outerBlockExecuted)
    }

}