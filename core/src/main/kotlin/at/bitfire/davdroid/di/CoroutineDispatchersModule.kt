/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import at.bitfire.davdroid.di.scope.DefaultDispatcher
import at.bitfire.davdroid.di.scope.IoDispatcher
import at.bitfire.davdroid.di.scope.MainDispatcher
import at.bitfire.davdroid.di.scope.SyncDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

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
     *
     * It uses the I/O dispatcher and limits the number of parallel operations to the number of available processors.
     */
    @Provides
    @SyncDispatcher
    @Singleton
    fun syncDispatcher(): CoroutineDispatcher =
        Dispatchers.IO.limitedParallelism(Runtime.getRuntime().availableProcessors())

}