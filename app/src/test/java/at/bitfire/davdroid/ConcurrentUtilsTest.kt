/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

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
            threads += Thread() {
                ConcurrentUtils.runSingle(i) {
                    nrCalled.incrementAndGet()
                    Thread.sleep(100)
                }
            }.apply { start() }
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
    fun testRunSingle_SameKey_Parallel() {
        val key = "a"
        val nrCalled = AtomicInteger()
        val threads = mutableListOf<Thread>()
        for (i in 0 until 10)
            threads += Thread() {
                ConcurrentUtils.runSingle(key) {
                    nrCalled.incrementAndGet()
                    Thread.sleep(100)
                }
            }.apply { start() }
        threads.forEach { it.join() }
        assertEquals(1, nrCalled.get())
    }

}