/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
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
    val colorScheme = if (!darkTheme)
        M3ColorScheme.lightScheme
    else
        M3ColorScheme.darkScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val barStyle = if (darkTheme) {
                SystemBarStyle.dark(colorScheme.primary.toArgb())
            } else {
                SystemBarStyle.light(
                    colorScheme.primary.toArgb(),
                    colorScheme.onPrimary.toArgb()
                )
            }
            (view.context as? AppCompatActivity)?.enableEdgeToEdge(barStyle, barStyle)
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