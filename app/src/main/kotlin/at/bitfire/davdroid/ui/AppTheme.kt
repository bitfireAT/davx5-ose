/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import at.bitfire.davdroid.ui.composable.SafeAndroidUriHandler

@Composable
fun M2Theme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme())
        M2Colors.dark
    else
        M2Colors.light

    CompositionLocalProvider(LocalUriHandler provides SafeAndroidUriHandler(LocalContext.current)) {
        androidx.compose.material.MaterialTheme(colors = colors) {
            content()
        }
    }
}

@Composable
fun AppTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (!useDarkTheme)
        M3ColorScheme.LightColors
    else
        M3ColorScheme.DarkColors

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}