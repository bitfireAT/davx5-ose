/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import at.bitfire.davdroid.ui.composable.SafeAndroidUriHandler

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (!darkTheme)
        M3ColorScheme.lightScheme
    else
        M3ColorScheme.darkScheme

    // If applicable, call Activity.enableEdgeToEdge to enable edge-to-edge layout on Android <15, too.
    // When we have moved everything into one Activity with Compose navigation, we can call it there instead.
    val view = LocalView.current
    SideEffect {
        (view.context as? AppCompatActivity)?.let { activity ->
            val systemBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
            activity.enableEdgeToEdge(systemBarStyle, systemBarStyle)
        }
    }

    // Apply SafeAndroidUriHandler to the composition
    val uriHandler = SafeAndroidUriHandler(LocalContext.current)
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {

        // There's no surrounding Box with Modifier.safeDrawingPadding() here, so the content() should
        // handle insets itself if necessary. Usually, it will be handled by M3 Scaffold / TopAppBar.

        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}