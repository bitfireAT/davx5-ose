/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.app.Activity
import android.os.Build
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import at.bitfire.davdroid.ui.composable.SafeAndroidUriHandler

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    statusBarColorProvider: @Composable (colorScheme: ColorScheme) -> Color? = { null },
    statusBarDarkColorProvider: @Composable (colorScheme: ColorScheme) -> Color? = { null },
    navigationBarColorProvider: @Composable (colorScheme: ColorScheme) -> Color? = { null },
    navigationBarDarkColorProvider: @Composable (colorScheme: ColorScheme) -> Color? = { null },
    content: @Composable () -> Unit
) {
    val colorScheme = if (!darkTheme)
        M3ColorScheme.lightScheme
    else
        M3ColorScheme.darkScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        val statusBarColor = statusBarColorProvider(colorScheme)
        val statusBarDarkColor = statusBarDarkColorProvider(colorScheme)
        val navigationBarColor = navigationBarColorProvider(colorScheme)
        val navigationBarDarkColor = navigationBarDarkColorProvider(colorScheme)
        SideEffect {
            (view.context as? AppCompatActivity)?.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(
                    (statusBarColor ?: colorScheme.surface).toArgb(),
                    (statusBarDarkColor ?: colorScheme.onSurface).toArgb()
                ) { darkTheme },
                navigationBarStyle = SystemBarStyle.auto(
                    (navigationBarColor ?: colorScheme.background).toArgb(),
                    (navigationBarDarkColor ?: colorScheme.onBackground).toArgb()
                ) { darkTheme }
            )

            // Only use on SDKs lower than 35
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE){
                val window = (view.context as Activity).window
                window.statusBarColor = (statusBarColor ?: colorScheme.surface).toArgb()
                window.navigationBarColor = (navigationBarColor ?: colorScheme.background).toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
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