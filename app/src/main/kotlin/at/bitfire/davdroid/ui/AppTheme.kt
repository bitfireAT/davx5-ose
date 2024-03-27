package at.bitfire.davdroid.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import at.bitfire.davdroid.ui.composable.SafeAndroidUriHandler

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme())
        ThemeColors.dark
    else
        ThemeColors.light

    CompositionLocalProvider(LocalUriHandler provides SafeAndroidUriHandler(LocalContext.current)) {
        Box(
            modifier = Modifier
                .imePadding()
                .navigationBarsPadding()
                .statusBarsPadding()
        ) {
            MaterialTheme(colors = colors) {
                content()
            }
        }
    }
}