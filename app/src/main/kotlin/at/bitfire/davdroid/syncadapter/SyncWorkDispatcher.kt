/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.syncadapter

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object SyncWorkDispatcher {

    private var _dispatcher: CoroutineDispatcher? = null

    /**
     * We use our own dispatcher to
     *
     *   - make sure that all threads have [Thread.getContextClassLoader] set,
     *     which is required for dav4jvm and ical4j (because they rely on [ServiceLoader]),
     *   - control the global number of sync worker threads.
     */
    @Synchronized
    fun getInstance(context: Context): CoroutineDispatcher {
        // prefer cached work dispatcher
        _dispatcher?.let { return it }

        val newDispatcher = createDispatcher(context.applicationContext.classLoader)
        _dispatcher = newDispatcher

        return newDispatcher
    }

    private fun createDispatcher(classLoader: ClassLoader) =
        ThreadPoolExecutor(
            0, Runtime.getRuntime().availableProcessors(),
            10, TimeUnit.SECONDS, LinkedBlockingQueue(),
            object: ThreadFactory {
                val group = ThreadGroup("sync-work")
                override fun newThread(r: Runnable) =
                    Thread(group, r).apply {
                        contextClassLoader = classLoader
                    }
            }
        ).asCoroutineDispatcher()

}