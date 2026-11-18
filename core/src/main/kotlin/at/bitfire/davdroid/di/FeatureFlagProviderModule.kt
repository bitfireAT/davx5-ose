/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import at.bitfire.davdroid.featureflags.DefaultFeatureFlagsProvider
import com.davx5.feature.featureflags.api.FeatureFlag
import com.davx5.feature.featureflags.api.FeatureFlagsProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class FeatureFlagProviderModule {
    @Binds
    abstract fun featureFlagsProvider(impl: DefaultFeatureFlagsProvider): FeatureFlagsProvider

    @Multibinds abstract fun featureFlags(): Set<FeatureFlag>
}
