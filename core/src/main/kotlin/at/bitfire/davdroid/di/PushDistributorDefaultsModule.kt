/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import at.bitfire.davdroid.push.PushDistributorDefaults
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface PushDistributorDefaultsModule {

    // allows binding empty Optional<PushDistributorDefaults>
    @BindsOptionalOf
    fun pushDistributorDefaults(): PushDistributorDefaults

}