/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import at.bitfire.davdroid.di.qualifier.DownloadSemaphore
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
     * Limits the total number of concurrent remote resource downloads across all active sync
     * operations. Shared as a singleton so that multiple simultaneously-syncing collections
     * don't saturate the network with more parallel requests than there are CPU cores.
     */
    @Provides
    @DownloadSemaphore
    @Singleton
    fun downloadSemaphore(): Semaphore =
        Semaphore(Runtime.getRuntime().availableProcessors())

}
