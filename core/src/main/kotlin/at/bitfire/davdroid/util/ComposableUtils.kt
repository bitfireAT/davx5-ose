/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Disable the ripple effect for Composables inside [content].
 */
@Composable
fun WithoutRipple(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        content()
    }
}
