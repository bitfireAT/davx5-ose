/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import at.bitfire.davdroid.ui.composable.SafeAndroidUriHandler

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    SideEffect {
        // If applicable, call Activity.enableEdgeToEdge to enable edge-to-edge layout on Android <15, too.
        // When we have moved everything into one Activity with Compose navigation, we can call it there instead.
        (view.context as? AppCompatActivity)?.enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = M3ColorScheme.lightScheme.scrim.toArgb(),
                darkScrim = M3ColorScheme.darkScheme.scrim.toArgb()
            ) { darkTheme }
        )
    }

    // Apply SafeAndroidUriHandler to the composition
    val uriHandler = SafeAndroidUriHandler(LocalContext.current)
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        MaterialTheme(
            colorScheme = if (!darkTheme)
                M3ColorScheme.lightScheme
            else
                M3ColorScheme.darkScheme
        ) {
            // Consume the navigation bars window insets. This forces the app to use all the space
            // available below, which makes the navigation bar overlap some content in scrollable
            // views. If desired, custom padding values can be applied to the individual components.
            Box(Modifier.consumeWindowInsets(WindowInsets.navigationBars)) {
                content()
            }
        }
    }
}