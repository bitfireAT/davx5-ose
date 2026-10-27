/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import at.bitfire.davdroid.di.qualifier.SyncMultigetSemaphore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.sync.Semaphore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class SyncModule {

    /** Semaphore limiting concurrent multiget downloads, app-wide (sized to CPU cores, clamped 2–8). */
    @Provides
    @SyncMultigetSemaphore
    @Singleton
    fun syncMultigetSemaphore(): Semaphore =
        Semaphore(Runtime.getRuntime().availableProcessors().coerceIn(2, 8))

}
