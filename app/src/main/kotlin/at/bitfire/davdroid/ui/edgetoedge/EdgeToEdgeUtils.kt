/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.edgetoedge

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration

@Composable
private fun isLandscape(): Boolean {
    val config = LocalConfiguration.current
    return config.orientation == ORIENTATION_LANDSCAPE
}

@Composable
fun <T> T.onlyPortrait(modifier: @Composable T.() -> T): T {
    return if (isLandscape()) {
        this
    } else {
        modifier()
    }
}

@Composable
fun <T> T.onlyLandscape(modifier: @Composable T.() -> T): T {
    return if (isLandscape()) {
        modifier()
    } else {
        this
    }
}

@Composable
fun <T> withOrientation(landscape: T, portrait: T): T {
    return if (isLandscape()) {
        landscape
    } else {
        portrait
    }
}

@Composable
fun NavigationBarSpacer() {
    Spacer(Modifier.navigationBarsPadding())
}
