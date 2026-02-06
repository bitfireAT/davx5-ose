/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.ose.di

import androidx.compose.material3.ColorScheme
import at.bitfire.davdroid.di.scope.DarkColorScheme
import at.bitfire.davdroid.di.scope.LightColorScheme
import at.bitfire.davdroid.ui.OseTheme
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class ColorSchemesModule {

    @Provides
    @LightColorScheme
    fun lightColorScheme(): ColorScheme = OseTheme.lightScheme

    @Provides
    @DarkColorScheme
    fun darkColorScheme(): ColorScheme = OseTheme.darkScheme

}
