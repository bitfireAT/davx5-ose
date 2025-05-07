/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class DefaultDispatcher

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class IoDispatcher

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class MainDispatcher

@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class SyncDispatcher

@Module
@InstallIn(SingletonComponent::class)
class CoroutineDispatchersModule {

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @MainDispatcher
    fun mainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    /**
     * A dispatcher for background sync operations. They're not run on [ioDispatcher] because there can
     * be many long-blocking operations at the same time which shouldn't never block other I/O operations
     * like database access for the UI.
     */
    @Provides
    @Singleton
    @SyncDispatcher
    fun syncDispatcher(): CoroutineDispatcher =
        ThreadPoolExecutor(
            /* corePoolSize = */ 0,
            /* maximumPoolSize = */ Runtime.getRuntime().availableProcessors(),
            /* keepAliveTime = */ 10,
            /* unit = */ TimeUnit.SECONDS,
            /* workQueue = */ LinkedBlockingQueue(),
            object : ThreadFactory {
                override fun newThread(r: Runnable?) = Thread(r, "SyncDispatcher")
            }
        ).asCoroutineDispatcher()

}