/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Used to track whether the app is in foreground (visible to user) or not.
 */
object ForegroundTracker {
    val inForeground: MutableStateFlow<Boolean> = MutableStateFlow(false)
}