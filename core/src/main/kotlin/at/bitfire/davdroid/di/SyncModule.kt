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
     * Semaphore to limit concurrent sync downloads/uploads, app-wide. (Currently only used for downloads.)
     *
     * Sized to the number of available processors, clamped to
     * - 2 (some overlap even on single-core devices) to
     * - 8 (avoid too many concurrent requests/local writes on high-core-count devices).
     */
    @Provides
    @SyncTransferSemaphore
    @Singleton
    fun syncTransferSemaphore(): Semaphore =
        Semaphore(Runtime.getRuntime().availableProcessors().coerceIn(2, 8))

}
