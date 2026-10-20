/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.preconfiguredsettings.externalscanner.di

import at.bitfire.davdroid.ui.setup.LoginType
import com.davx5.feature.preconfiguredsettings.api.PreconfiguredSettingsType
import com.davx5.feature.preconfiguredsettings.externalscanner.PreconfiguredSettingsLogin
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object PreconfiguredSettingsProviderModule {

    @Provides
    @PreconfiguredSettingsType
    fun preconfiguredSettingsType(): LoginType = PreconfiguredSettingsLogin
}
