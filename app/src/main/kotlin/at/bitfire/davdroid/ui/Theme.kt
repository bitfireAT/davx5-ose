package at.bitfire.davdroid.ui

import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import at.bitfire.davdroid.ui.composable.SafeAndroidUriHandler

private val grey100 = Color(0xfff5f5f5)
private val grey200 = Color(0xffeeeeee)
private val grey800 = Color(0xff424242)
private val grey1000 = Color(0xff121212)
private val red700 = Color(0xffd32f2f)

val primaryGreen = Color(0xff7cb342)
val primaryDarkGreen = Color(0xff4b830d)
val onPrimaryGreen = Color(0xff000000)
val secondaryOrange = Color(0xffff6d00)
val secondaryLightOrange = Color(0xffff9e40)
val onSecondaryOrange = Color(0xfffafafa)

private val colorsDark = Colors(
    primary = primaryGreen,
    primaryVariant = primaryGreen,
    onPrimary = onPrimaryGreen,
    secondary = secondaryOrange,
    secondaryVariant = secondaryLightOrange,
    onSecondary = Color.Black,
    background = grey800,
    onBackground = grey100,
    surface = grey1000,
    onSurface = grey100,
    error = red700,
    onError = grey100,
    isLight = false
)

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
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current

    val colors = if (darkTheme) colorsDark else colorsLight

    LaunchedEffect(context, colors) {
        (context as? AppCompatActivity)?.enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.dark(
                scrim = Color.Black.toArgb()
            ),
            statusBarStyle = SystemBarStyle.dark(
                scrim = primaryDarkGreen.toArgb()
            )
        )
    }

    CompositionLocalProvider(LocalUriHandler provides SafeAndroidUriHandler(context)) {
        Box(
            modifier = Modifier
                .imePadding()
                .navigationBarsPadding()
                .statusBarsPadding()
        ) {
            MaterialTheme(
                colors = colors
            ) {
                content()
            }
        }
    }
}