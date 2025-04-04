/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bitfire.davdroid.ui.composable.SafeAndroidUriHandler
import at.bitfire.davdroid.ui.edgetoedge.LocalStatusBarScrimColors
import at.bitfire.davdroid.ui.edgetoedge.StatusBarScrimColors

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) = CompositionLocalProvider(
    LocalStatusBarScrimColors provides StatusBarScrimColors(
        initialStatusBarDarkTheme = darkTheme
    )
) {
    val activity = LocalActivity.current
    val statusBarDarkMode by LocalStatusBarScrimColors.current.statusBarDarkTheme.collectAsStateWithLifecycle()
    val navigationBarDarkTheme by LocalStatusBarScrimColors.current.navigationBarDarkTheme.collectAsStateWithLifecycle()
    LaunchedEffect(statusBarDarkMode, navigationBarDarkTheme) {
        // If applicable, call Activity.enableEdgeToEdge to enable edge-to-edge layout on Android <15, too.
        // When we have moved everything into one Activity with Compose navigation, we can call it there instead.
        (activity as? AppCompatActivity)?.enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = M3ColorScheme.lightScheme.scrim.toArgb(),
                darkScrim = M3ColorScheme.darkScheme.scrim.toArgb()
            ) { navigationBarDarkTheme },
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = M3ColorScheme.lightScheme.scrim.toArgb(),
                darkScrim = M3ColorScheme.darkScheme.scrim.toArgb()
            ) { statusBarDarkMode }
        )
    }

    // Apply SafeAndroidUriHandler to the composition
    val uriHandler = SafeAndroidUriHandler(LocalContext.current)
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        MaterialTheme(
            colorScheme = if (!darkTheme)
                M3ColorScheme.lightScheme
            else
                M3ColorScheme.darkScheme,
            content = content,
        )
    }
    
    // Track if the app is in the foreground
    val view = LocalView.current
    LifecycleResumeEffect(view) {
        ForegroundTracker.onResume()
        onPauseOrDispose {
            ForegroundTracker.onPaused()
        }
    }
}