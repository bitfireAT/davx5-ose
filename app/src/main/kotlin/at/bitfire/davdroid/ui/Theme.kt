package at.bitfire.davdroid.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import at.bitfire.davdroid.ui.widget.SafeAndroidUriHandler
import com.google.accompanist.themeadapter.material.MdcTheme

val primaryGreen = Color(0xff7cb342)
val onPrimaryGreen = Color(0xfffafafa)

@Suppress("DEPRECATION")
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalUriHandler provides SafeAndroidUriHandler(LocalContext.current)) {
        MdcTheme {
            content()
        }
    }
}