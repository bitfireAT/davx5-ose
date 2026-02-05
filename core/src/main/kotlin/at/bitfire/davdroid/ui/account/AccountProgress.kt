/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

/** Tri-state enum to represent active / pending / idle status */
enum class AccountProgress {
    Active,     // syncing or refreshing
    Pending,    // sync pending
    Idle;       // idle

    @Composable
    fun rememberAlpha(): Float {
        val progressAlpha by animateFloatAsState(
            when (this@AccountProgress) {
                Active -> 1f
                Pending -> 0.5f
                Idle -> 0f
            },
            label = "progressAlpha",
            animationSpec = tween(500)
        )
        return progressAlpha
    }

}