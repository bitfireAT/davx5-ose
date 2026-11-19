/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.featureflags.template.experimental.di

import com.davx5.feature.experimentalsettings.ui.ExperimentalSetting
import com.davx5.feature.featureflags.api.FeatureFlag
import com.davx5.feature.featureflags.template.experimental.TemplateExperimentalSetting
import com.davx5.feature.featureflags.template.experimental.TemplateFeatureFlag
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
interface TemplateFeatureModule {
    @Binds
    @IntoSet
    fun featureFlag(impl: TemplateFeatureFlag): FeatureFlag
}

@Module
@InstallIn(ActivityComponent::class)
interface TemplateExperimentalSettingModule {
    @Binds
    @IntoSet
    fun experimentalSetting(impl: TemplateExperimentalSetting): ExperimentalSetting
}
