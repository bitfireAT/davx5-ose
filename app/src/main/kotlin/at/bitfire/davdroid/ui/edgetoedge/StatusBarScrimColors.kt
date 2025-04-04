/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.edgetoedge

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class StatusBarScrimColors(
    private val initialStatusBarDarkTheme: Boolean = false,
    private val initialNavigationBarDarkTheme: Boolean = initialStatusBarDarkTheme,
) {
    private val _statusBarDarkTheme = MutableStateFlow(initialStatusBarDarkTheme)
    val statusBarDarkTheme get() = _statusBarDarkTheme.asStateFlow()

    private val _navigationBarDarkTheme = MutableStateFlow(initialNavigationBarDarkTheme)
    val navigationBarDarkTheme get() = _navigationBarDarkTheme.asStateFlow()

    fun setStatusBarDarkTheme(darkTheme: Boolean) {
        _statusBarDarkTheme.tryEmit(darkTheme)
    }
    fun resetStatusBarDarkTheme() {
        _statusBarDarkTheme.tryEmit(initialStatusBarDarkTheme)
    }

    fun setNavigationBarDarkTheme(darkTheme: Boolean) {
        _navigationBarDarkTheme.tryEmit(darkTheme)
    }
    fun resetNavigationBarDarkTheme() {
        _navigationBarDarkTheme.tryEmit(initialNavigationBarDarkTheme)
    }

    fun reset() {
        resetStatusBarDarkTheme()
        resetNavigationBarDarkTheme()
    }
}

val LocalStatusBarScrimColors = compositionLocalOf { StatusBarScrimColors() }
