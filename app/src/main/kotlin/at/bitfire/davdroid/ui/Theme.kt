package at.bitfire.davdroid.ui

import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import at.bitfire.davdroid.ui.composable.SafeAndroidUriHandler

private val grey100 = Color(0xfff5f5f5)
private val grey200 = Color(0xffeeeeee)
private val red700 = Color(0xffd32f2f)

val primaryGreen = Color(0xff7cb342)
val onPrimaryGreen = Color(0xfffafafa)
val secondaryOrange = Color(0xffff6d00)
val secondaryLightOrange = Color(0xffff9e40)
val onSecondaryOrange = Color(0xfffafafa)

private val colorsLight = Colors(
    primary = primaryGreen,
    primaryVariant = primaryGreen,
    onPrimary = onPrimaryGreen,
    secondary = secondaryOrange,
    secondaryVariant = secondaryLightOrange,
    onSecondary = onSecondaryOrange,
    background = grey100,
    onBackground = Color.Black,
    surface = grey200,
    onSurface = Color.Black,
    error = red700,
    onError = grey100,
    isLight = true
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalUriHandler provides SafeAndroidUriHandler(LocalContext.current)) {
        MaterialTheme(
            colors = colorsLight
        ) {
            content()
        }
    }
}