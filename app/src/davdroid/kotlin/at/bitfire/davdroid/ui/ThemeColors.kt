/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

@Suppress("MemberVisibilityCanBePrivate")
object M3ColorScheme {

    // All colors hand-crafted because Material Theme Builder generates unbelievably ugly colors

    val primaryLight = Color(0xFF7cb342)
    val onPrimaryLight = Color(0xFFffffff)
    val primaryContainerLight = Color(0xFFb4e47d)
    val onPrimaryContainerLight = Color(0xFF232d18)
    val secondaryLight = Color(0xFFff7f2a)
    val onSecondaryLight = Color(0xFFFFFFFF)
    val secondaryContainerLight = Color(0xFFffa565)
    val onSecondaryContainerLight = Color(0xFF3a271b)
    val tertiaryLight = Color(0xFF658a24)
    val onTertiaryLight = Color(0xFFFFFFFF)
    val tertiaryContainerLight = Color(0xFFb0d08e)
    val onTertiaryContainerLight = Color(0xFF263015)
    val errorLight = Color(0xFFd71717)
    val onErrorLight = Color(0xFFFFFFFF)
    val errorContainerLight = Color(0xFFefb6b6)
    val onErrorContainerLight = Color(0xFF3a0b0b)
    val backgroundLight = Color(0xFFfcfcfc)
    val onBackgroundLight = Color(0xFF2a2a2a)
    val surfaceLight = Color(0xFFf5f5f5)
    val onSurfaceLight = Color(0xFF4d4d4d)
    val surfaceVariantLight = Color(0xFFe4e4e4)
    val onSurfaceVariantLight = Color(0xFF2a2a2a)
    val outlineLight = Color(0xFF838383)
    val outlineVariantLight = Color(0xFFd4d4d4)
    val scrimLight = Color(0xFF000000)
    val inverseSurfaceLight = Color(0xFF2e322b)
    val inverseOnSurfaceLight = Color(0xFFfafaf8)
    val inversePrimaryLight = Color(0xFFb4e47d)
    val surfaceDimLight = Color(0xFFe3e3e3)
    val surfaceBrightLight = Color(0xFFf9f9f9)
    val surfaceContainerLowestLight = Color(0xFFFFFFFF)
    val surfaceContainerLowLight = Color(0xFFfafafa)
    val surfaceContainerLight = Color(0xFFf5f5f5)
    val surfaceContainerHighLight = Color(0xFFf0f0ef)
    val surfaceContainerHighestLight = Color(0xFFebebea)

    val primaryDark = Color(0xFFc4e3a4)
    val onPrimaryDark = Color(0xFF2b4310)
    val primaryContainerDark = Color(0xFF7cb342)
    val onPrimaryContainerDark = Color(0xFFedf5e4)
    val secondaryDark = Color(0xFFe5c3ac)
    val onSecondaryDark = Color(0xFF3e332e)
    val secondaryContainerDark = Color(0xFFff7f2a)
    val onSecondaryContainerDark = Color(0xFFffeadb)
    val tertiaryDark = Color(0xFFc6e597)
    val onTertiaryDark = Color(0xFF4b661b)
    val tertiaryContainerDark = Color(0xFF658a24)
    val onTertiaryContainerDark = Color(0xFFf0f8e2)
    val errorDark = Color(0xFFf6d0d0)
    val onErrorDark = Color(0xFF4f1212)
    val errorContainerDark = Color(0xFFe93434)
    val onErrorContainerDark = Color(0xFFfcdede)
    val backgroundDark = Color(0xFF1a1a1a)
    val onBackgroundDark = Color(0xFFf0f0f0)
    val surfaceDark = Color(0xFF292929)
    val onSurfaceDark = Color(0xFFdedede)
    val surfaceVariantDark = Color(0xFF363636)
    val onSurfaceVariantDark = Color(0xFFededed)
    val outlineDark = Color(0xFFa3a3a3)
    val outlineVariantDark = Color(0xFF7cb342)
    val scrimDark = Color(0xFF000000)
    val inverseSurfaceDark = Color(0xFFdbdbdb)
    val inverseOnSurfaceDark = Color(0xFF292929)
    val inversePrimaryDark = Color(0xFF7cb342)
    val surfaceDimDark = Color(0xFF333333)
    val surfaceBrightDark = Color(0xFF4d4d4d)
    val surfaceContainerLowestDark = Color(0xFF141414)
    val surfaceContainerLowDark = Color(0xFF1f1f1f)
    val surfaceContainerDark = Color(0xFFf5f5f5)
    val surfaceContainerHighDark = Color(0xFF383838)
    val surfaceContainerHighestDark = Color(0xFF434343)


    // Copied from Material Theme Builder: Theme.kt

    val lightScheme = lightColorScheme(
        primary = primaryLight,
        onPrimary = onPrimaryLight,
        primaryContainer = primaryContainerLight,
        onPrimaryContainer = onPrimaryContainerLight,
        secondary = secondaryLight,
        onSecondary = onSecondaryLight,
        secondaryContainer = secondaryContainerLight,
        onSecondaryContainer = onSecondaryContainerLight,
        tertiary = tertiaryLight,
        onTertiary = onTertiaryLight,
        tertiaryContainer = tertiaryContainerLight,
        onTertiaryContainer = onTertiaryContainerLight,
        error = errorLight,
        onError = onErrorLight,
        errorContainer = errorContainerLight,
        onErrorContainer = onErrorContainerLight,
        background = backgroundLight,
        onBackground = onBackgroundLight,
        surface = surfaceLight,
        onSurface = onSurfaceLight,
        surfaceVariant = surfaceVariantLight,
        onSurfaceVariant = onSurfaceVariantLight,
        outline = outlineLight,
        outlineVariant = outlineVariantLight,
        scrim = scrimLight,
        inverseSurface = inverseSurfaceLight,
        inverseOnSurface = inverseOnSurfaceLight,
        inversePrimary = inversePrimaryLight,
        surfaceDim = surfaceDimLight,
        surfaceBright = surfaceBrightLight,
        surfaceContainerLowest = surfaceContainerLowestLight,
        surfaceContainerLow = surfaceContainerLowLight,
        surfaceContainer = surfaceContainerLight,
        surfaceContainerHigh = surfaceContainerHighLight,
        surfaceContainerHighest = surfaceContainerHighestLight,
    )

    val darkScheme = darkColorScheme(
        primary = primaryDark,
        onPrimary = onPrimaryDark,
        primaryContainer = primaryContainerDark,
        onPrimaryContainer = onPrimaryContainerDark,
        secondary = secondaryDark,
        onSecondary = onSecondaryDark,
        secondaryContainer = secondaryContainerDark,
        onSecondaryContainer = onSecondaryContainerDark,
        tertiary = tertiaryDark,
        onTertiary = onTertiaryDark,
        tertiaryContainer = tertiaryContainerDark,
        onTertiaryContainer = onTertiaryContainerDark,
        error = errorDark,
        onError = onErrorDark,
        errorContainer = errorContainerDark,
        onErrorContainer = onErrorContainerDark,
        background = backgroundDark,
        onBackground = onBackgroundDark,
        surface = surfaceDark,
        onSurface = onSurfaceDark,
        surfaceVariant = surfaceVariantDark,
        onSurfaceVariant = onSurfaceVariantDark,
        outline = outlineDark,
        outlineVariant = outlineVariantDark,
        scrim = scrimDark,
        inverseSurface = inverseSurfaceDark,
        inverseOnSurface = inverseOnSurfaceDark,
        inversePrimary = inversePrimaryDark,
        surfaceDim = surfaceDimDark,
        surfaceBright = surfaceBrightDark,
        surfaceContainerLowest = surfaceContainerLowestDark,
        surfaceContainerLow = surfaceContainerLowDark,
        surfaceContainer = surfaceContainerDark,
        surfaceContainerHigh = surfaceContainerHighDark,
        surfaceContainerHighest = surfaceContainerHighestDark,
    )

}