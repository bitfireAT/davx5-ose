/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Used to track whether the app is in foreground (visible to user) or not.
 */
object ForegroundTracker {

    /**
     * Whether the app is in the foreground.
     * Used by cert4android to known when it's possible to show the certificate trust decision dialog.
     */
    private val _inForeground = MutableStateFlow(false)

    /**
     * Whether the app is in foreground or not.
     */
    val inForeground = _inForeground.asStateFlow()

    /**
     * Called when the app is resumed (at [androidx.lifecycle.Lifecycle.Event.ON_RESUME])
     */
    fun onResume() {
        _inForeground.value = true
    }

    /**
     * Called when the app is paused (at [androidx.lifecycle.Lifecycle.Event.ON_PAUSE])
     */
    fun onPaused() {
        _inForeground.value = false
    }

}