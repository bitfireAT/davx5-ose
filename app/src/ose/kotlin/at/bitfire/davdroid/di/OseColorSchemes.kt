/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.di

import androidx.compose.material3.ColorScheme
import at.bitfire.davdroid.di.scopes.DarkColorScheme
import at.bitfire.davdroid.di.scopes.LightColorScheme
import at.bitfire.davdroid.ui.M3ColorScheme
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class OseColorSchemes {
    @Provides
    @LightColorScheme
    fun lightColorScheme(): ColorScheme = M3ColorScheme.lightScheme

    @Provides
    @DarkColorScheme
    fun darkColorScheme(): ColorScheme = M3ColorScheme.darkScheme
}
