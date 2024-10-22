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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? AppCompatActivity
            val systemBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
            activity?.enableEdgeToEdge(systemBarStyle, systemBarStyle)
        }
    }

    val uriHandler = SafeAndroidUriHandler(LocalContext.current)
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}