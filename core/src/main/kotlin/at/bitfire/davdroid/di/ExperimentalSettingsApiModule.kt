/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import com.davx5.feature.experimentalsettings.api.ExperimentalSettingsUi
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
interface ExperimentalSettingsApiModule {
    @BindsOptionalOf
    fun experimentalSettingsUi(): ExperimentalSettingsUi
}
