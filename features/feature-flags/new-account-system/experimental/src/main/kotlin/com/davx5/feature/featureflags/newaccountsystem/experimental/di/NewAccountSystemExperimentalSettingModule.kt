/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.featureflags.newaccountsystem.experimental.di

import com.davx5.feature.experimentalsettings.ui.ExperimentalSetting
import com.davx5.feature.featureflags.api.FeatureFlag
import com.davx5.feature.featureflags.newaccountsystem.experimental.NewAccountSystemExperimentalSetting
import com.davx5.feature.featureflags.newaccountsystem.experimental.NewAccountSystemFeatureFlag
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface NewAccountSystemFeatureModule {
    @Binds
    @IntoSet
    fun featureFlag(impl: NewAccountSystemFeatureFlag): FeatureFlag
}

@Module
@InstallIn(ActivityComponent::class)
interface NewAccountSystemExperimentalSettingModule {
    @Binds
    @IntoSet
    fun experimentalSetting(impl: NewAccountSystemExperimentalSetting): ExperimentalSetting
}
