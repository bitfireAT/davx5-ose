/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.composable

import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LifecycleResumeEffect
import at.bitfire.davdroid.di.scope.DarkColorScheme
import at.bitfire.davdroid.di.scope.LightColorScheme
import at.bitfire.davdroid.ui.ForegroundTracker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AppThemeEntryPoint {
    @LightColorScheme
    fun lightColorScheme(): ColorScheme

    @DarkColorScheme
    fun darkColorScheme(): ColorScheme
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val entryPoint by lazy { EntryPointAccessors.fromApplication<AppThemeEntryPoint>(context) }
    val lightColorScheme = remember { entryPoint.lightColorScheme() }
    val darkColorScheme = remember { entryPoint.darkColorScheme() }

    val activity = LocalActivity.current
    SideEffect {
        // If applicable, call Activity.enableEdgeToEdge to enable edge-to-edge layout on Android <15, too.
        // When we have moved everything into one Activity with Compose navigation, we can call it there instead.
        (activity as? AppCompatActivity)?.enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = lightColorScheme.scrim.toArgb(),
                darkScrim = darkColorScheme.scrim.toArgb()
            ) { darkTheme }
        )
    }

    // Apply SafeAndroidUriHandler to the composition
    val uriHandler = SafeAndroidUriHandler(LocalContext.current)
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkColorScheme else lightColorScheme
        ) {
            Box(Modifier.windowInsetsPadding(windowInsets).clipToBounds()) {
                content()
            }
        }
    }
    
    // Track if the app is in the foreground
    val view = LocalView.current
    LifecycleResumeEffect(view) {
        ForegroundTracker.onResume()
        onPauseOrDispose {
            ForegroundTracker.onPaused()
        }
    }
}