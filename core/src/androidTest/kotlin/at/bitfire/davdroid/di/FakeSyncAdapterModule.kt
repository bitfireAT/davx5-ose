/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import at.bitfire.davdroid.sync.FakeSyncAdapter
import at.bitfire.davdroid.sync.adapter.SyncAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [SyncAdapterModule::class])
interface FakeSyncAdapterModule {
    @Binds
    fun syncAdapter(impl: FakeSyncAdapter): SyncAdapter
}
