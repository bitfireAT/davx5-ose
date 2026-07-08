/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import at.bitfire.davdroid.di.qualifier.DefaultDispatcher
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.di.qualifier.MainDispatcher
import at.bitfire.davdroid.di.qualifier.SyncTransferSemaphore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
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
     * Limits how many sync uploads/downloads may run at the same time, app-wide, so that a sync
     * of a huge collection doesn't open an unbounded number of concurrent HTTP requests / local
     * storage writes. Only meant to be acquired around the actual network transfer of a resource
     * (upload or download) — not around unrelated sync work like listing or local lookups, so
     * that a slow/blocking transfer can't starve unrelated sync operations.
     */
    @Provides
    @SyncTransferSemaphore
    @Singleton
    fun syncTransferSemaphore(): Semaphore = Semaphore(Runtime.getRuntime().availableProcessors())

}