/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.webdav

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class BlockingLifoQueueTest {

    var queue = BlockingLifoQueue<Int>()

    @Before
    fun prepare() {
        queue.clear()
        queue.addAll(arrayOf(1, 2))
    }

    @Test
    fun testElement() {
        assertEquals(2, queue.element())
    }

    @Test
    fun testPeek() {
        assertEquals(2, queue.peek())
    }

    @Test
    fun testPoll() {
        assertEquals(2, queue.poll())
        assertEquals(1, queue.poll())
    }

    @Test
    fun testPoll_Timeout() {
        assertEquals(2, queue.poll(1, TimeUnit.SECONDS))
        assertEquals(1, queue.poll(1, TimeUnit.SECONDS))
    }

    @Test
    fun testRemove() {
        assertEquals(2, queue.remove())
        assertEquals(1, queue.remove())
    }

    @Test
    fun testTake() {
        assertEquals(2, queue.take())
        assertEquals(1, queue.take())
    }

}