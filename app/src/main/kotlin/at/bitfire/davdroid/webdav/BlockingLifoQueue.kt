/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.webdav

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

class BlockingLifoQueue<E>(
    val base: LinkedBlockingDeque<E> = LinkedBlockingDeque<E>()
): BlockingQueue<E> by base {

    override fun element(): E = base.last
    override fun peek(): E? = base.peekLast()
    override fun poll(): E? = base.pollLast()
    override fun poll(timeout: Long, unit: TimeUnit?): E? = base.pollLast(timeout, unit)
    override fun remove(): E = base.removeLast()
    override fun take(): E = base.takeLast()

}