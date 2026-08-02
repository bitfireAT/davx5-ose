/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import at.bitfire.davdroid.sync.adapter.SyncAdapter
import at.bitfire.davdroid.sync.adapter.SyncAdapterImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface SyncAdapterModule {
    @Binds
    fun syncAdapter(impl: SyncAdapterImpl): SyncAdapter
}
