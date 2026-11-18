/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.experimentalsettings.ui.di

import com.davx5.feature.experimentalsettings.api.ExperimentalSettingsUi
import com.davx5.feature.experimentalsettings.ui.DefaultExperimentalSettingsUi
import com.davx5.feature.experimentalsettings.ui.ExperimentalSetting
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
abstract class ExperimentalSettingsUiModule {
    @Binds
    abstract fun experimentalSettingsUi(impl: DefaultExperimentalSettingsUi): ExperimentalSettingsUi

    @Multibinds
    abstract fun experimentalSettings(): Set<ExperimentalSetting>
}
