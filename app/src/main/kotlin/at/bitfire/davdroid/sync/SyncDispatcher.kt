/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.app.Application
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates a [CoroutineDispatcher] with multiple threads that guarantees that the threads
 * have set their contextClassLoader to the application context's class loader.
 *
 * We use our own dispatcher to
 *
 * - make sure that all threads have [Thread.getContextClassLoader] set, which is required for ical4j (because it uses [ServiceLoader]),
 * - control the global number of sync threads.
 */
@Singleton
class SyncDispatcher @Inject constructor(
    context: Application
) {

    val dispatcher = createDispatcher(context.classLoader)

    private fun createDispatcher(classLoader: ClassLoader): CoroutineDispatcher =
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