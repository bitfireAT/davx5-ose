package at.bitfire.davdroid.ui.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavController

val LocalNavController = compositionLocalOf<NavController> {
    error("No NavController attached.")
}