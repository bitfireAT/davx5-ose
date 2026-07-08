/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import at.bitfire.davdroid.di.qualifier.SyncTransferSemaphore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.sync.Semaphore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class SyncModule {

    /**
     * Limits how many sync uploads/downloads may run at the same time, app-wide, so that a sync
     * of a huge collection doesn't open an unbounded number of concurrent HTTP requests / local
     * storage writes. Only meant to be acquired around the actual network transfer of a resource
     * (upload or download) — not around unrelated sync work like listing or local lookups, so
     * that a slow/blocking transfer can't starve unrelated sync operations.
     *
     * Sized to the number of available processors, but at least 2 (so single-core devices still
     * get some overlap between transfers) and at most 8 (to avoid opening too many concurrent
     * requests/local writes on very high-core-count devices).
     */
    @Provides
    @SyncTransferSemaphore
    @Singleton
    fun syncTransferSemaphore(): Semaphore =
        Semaphore(Runtime.getRuntime().availableProcessors().coerceIn(2, 8))

}
