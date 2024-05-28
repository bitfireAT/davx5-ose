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
    val onSurfaceLight = Color(0xFF2d2d2d)
    val surfaceVariantLight = Color(0xFFdedede)
    val onSurfaceVariantLight = Color(0xFF757575)
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

    val primaryDark = Color(0xFF7cb342)
    val onPrimaryDark = Color(0xFFFFFFFF)
    val primaryContainerDark = Color(0xFF7cb342)
    val onPrimaryContainerDark = Color(0xFFFFFFFF)
    val secondaryDark = Color(0xFFFF7F2A)
    val onSecondaryDark = Color(0xFFFFFFFF)
    val secondaryContainerDark = Color(0xFFFF7F2A)
    val onSecondaryContainerDark = Color(0xFFFFFFFF)
    val tertiaryDark = Color(0xFF4b830d)
    val onTertiaryDark = Color(0xFFFFFFFF)
    val tertiaryContainerDark = Color(0xFF4b830d)
    val onTertiaryContainerDark = Color(0xFF002201)
    val errorDark = Color(0xFFFFB4AB)
    val onErrorDark = Color(0xFF690005)
    val errorContainerDark = Color(0xFF93000A)
    val onErrorContainerDark = Color(0xFFFFDAD6)
    val backgroundDark = Color(0xFF11140F)
    val onBackgroundDark = Color(0xFFE0E4DA)
    val surfaceDark = Color(0xFF1c1c1c)
    val onSurfaceDark = Color(0xFFE0E4DA)
    val surfaceVariantDark = Color(0xFF2a2a2a)
    val onSurfaceVariantDark = Color(0xFFC2C8BC)
    val outlineDark = Color(0xFF8C9387)
    val outlineVariantDark = Color(0xFF42493F)
    val scrimDark = Color(0xFF000000)
    val inverseSurfaceDark = Color(0xFFE0E4DA)
    val inverseOnSurfaceDark = Color(0xFF2E322B)
    val inversePrimaryDark = Color(0xFF3E6837)
    val surfaceDimDark = Color(0xFF11140F)
    val surfaceBrightDark = Color(0xFF363A34)
    val surfaceContainerLowestDark = Color(0xFF0B0F0A)
    val surfaceContainerLowDark = Color(0xFF191D17)
    val surfaceContainerDark = Color(0xFF1D211B)
    val surfaceContainerHighDark = Color(0xFF272B25)
    val surfaceContainerHighestDark = Color(0xFF32362F)


    // copied from Material Theme Builder: Theme.kt

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