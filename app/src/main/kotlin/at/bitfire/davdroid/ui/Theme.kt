package at.bitfire.davdroid.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import at.bitfire.davdroid.ui.widget.SafeAndroidUriHandler
import com.google.accompanist.themeadapter.material.MdcTheme

@Suppress("DEPRECATION")
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalUriHandler provides SafeAndroidUriHandler(LocalContext.current)) {
        MdcTheme {
            content()
        }
    }
}