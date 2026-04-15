/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.composable

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Generic interface for composables from different app flavors that can be displayed in various screens.
 */
interface FlavorComposable {

    /**
     * Composable function that renders this item.
     * @param modifier Modifier to apply to the composable
     */
    @Composable
    fun Render(modifier: Modifier = Modifier)

}

@Module
@InstallIn(SingletonComponent::class)
interface FlavorComposablesModule {
    // Provide empty set as default
    @Multibinds
    fun flavorComposables(): Set<@JvmSuppressWildcards FlavorComposable>
}