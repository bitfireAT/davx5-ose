/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.actioncards

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import javax.inject.Inject

/**
 * Interface for providing action cards that should be shown in the accounts screen.
 * Different build flavors can provide their own implementations.
 */
interface ActionCardProvider {

    /**
     * Composable function that renders action cards for the accounts screen.
     * @param modifier Modifier to apply to the action card container
     */
    @Composable
    fun ProvideActionCards(modifier: Modifier = Modifier)

    /**
     * Empty default implementation that provides no action cards.
     */
    class Empty @Inject constructor() : ActionCardProvider {
        @Composable
        override fun ProvideActionCards(modifier: Modifier) {
            // No extra action cards by default
        }
    }
}