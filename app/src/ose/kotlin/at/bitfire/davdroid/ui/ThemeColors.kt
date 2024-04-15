/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import androidx.compose.material.Colors
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object M2Colors {

    private val grey100 = Color(0xfff5f5f5)
    private val grey200 = Color(0xffeeeeee)
    private val grey800 = Color(0xff424242)
    private val grey1000 = Color(0xff121212)
    private val red700 = Color(0xffd32f2f)

    val primary = Color(0xff7cb342)
    val primaryDark = Color(0xff4b830d)
    val onPrimary = Color(0xff000000)
    val secondary = Color(0xffff6d00)
    val secondaryLight = Color(0xffff9e40)
    val onSecondary = Color(0xfffafafa)

    val light = Colors(
        primary = primary,
        primaryVariant = primaryDark,
        onPrimary = onPrimary,
        secondary = secondary,
        secondaryVariant = secondaryLight,
        onSecondary = onSecondary,
        background = grey100,
        onBackground = Color.Black,
        surface = grey200,
        onSurface = Color.Black,
        error = red700,
        onError = grey100,
        isLight = true
    )

    val dark = Colors(
        primary = primary,
        primaryVariant = primaryDark,
        onPrimary = onPrimary,
        secondary = secondary,
        secondaryVariant = secondaryLight,
        onSecondary = Color.Black,
        background = grey800,
        onBackground = grey100,
        surface = grey1000,
        onSurface = grey100,
        error = red700,
        onError = grey100,
        isLight = false
    )

}

object M3ColorScheme {

    val md_theme_light_primary = Color(0xFF914C00)
    val md_theme_light_onPrimary = Color(0xFFFFFFFF)
    val md_theme_light_primaryContainer = Color(0xFFFFDCC4)
    val md_theme_light_onPrimaryContainer = Color(0xFF2F1500)
    val md_theme_light_secondary = Color(0xFF3C6A00)
    val md_theme_light_onSecondary = Color(0xFFFFFFFF)
    val md_theme_light_secondaryContainer = Color(0xFFB8F47A)
    val md_theme_light_onSecondaryContainer = Color(0xFF0E2000)
    val md_theme_light_tertiary = Color(0xFF3C6A00)
    val md_theme_light_onTertiary = Color(0xFFFFFFFF)
    val md_theme_light_tertiaryContainer = Color(0xFFB8F47A)
    val md_theme_light_onTertiaryContainer = Color(0xFF0E2000)
    val md_theme_light_error = Color(0xFFBA1A1A)
    val md_theme_light_errorContainer = Color(0xFFFFDAD6)
    val md_theme_light_onError = Color(0xFFFFFFFF)
    val md_theme_light_onErrorContainer = Color(0xFF410002)
    val md_theme_light_background = Color(0xFFFFFBFF)
    val md_theme_light_onBackground = Color(0xFF201A17)
    val md_theme_light_surface = Color(0xFFFFFBFF)
    val md_theme_light_onSurface = Color(0xFF201A17)
    val md_theme_light_surfaceVariant = Color(0xFFF3DFD2)
    val md_theme_light_onSurfaceVariant = Color(0xFF51443B)
    val md_theme_light_outline = Color(0xFF847469)
    val md_theme_light_inverseOnSurface = Color(0xFFFAEEE8)
    val md_theme_light_inverseSurface = Color(0xFF352F2B)
    val md_theme_light_inversePrimary = Color(0xFFFFB77F)
    val md_theme_light_shadow = Color(0xFF000000)
    val md_theme_light_surfaceTint = Color(0xFF914C00)
    val md_theme_light_outlineVariant = Color(0xFFD6C3B7)
    val md_theme_light_scrim = Color(0xFF000000)

    val md_theme_dark_primary = Color(0xFFFFB77F)
    val md_theme_dark_onPrimary = Color(0xFF4E2600)
    val md_theme_dark_primaryContainer = Color(0xFF6F3900)
    val md_theme_dark_onPrimaryContainer = Color(0xFFFFDCC4)
    val md_theme_dark_secondary = Color(0xFF9DD761)
    val md_theme_dark_onSecondary = Color(0xFF1D3700)
    val md_theme_dark_secondaryContainer = Color(0xFF2C5000)
    val md_theme_dark_onSecondaryContainer = Color(0xFFB8F47A)
    val md_theme_dark_tertiary = Color(0xFF9DD761)
    val md_theme_dark_onTertiary = Color(0xFF1D3700)
    val md_theme_dark_tertiaryContainer = Color(0xFF2C5000)
    val md_theme_dark_onTertiaryContainer = Color(0xFFB8F47A)
    val md_theme_dark_error = Color(0xFFFFB4AB)
    val md_theme_dark_errorContainer = Color(0xFF93000A)
    val md_theme_dark_onError = Color(0xFF690005)
    val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
    val md_theme_dark_background = Color(0xFF201A17)
    val md_theme_dark_onBackground = Color(0xFFECE0DA)
    val md_theme_dark_surface = Color(0xFF201A17)
    val md_theme_dark_onSurface = Color(0xFFECE0DA)
    val md_theme_dark_surfaceVariant = Color(0xFF51443B)
    val md_theme_dark_onSurfaceVariant = Color(0xFFD6C3B7)
    val md_theme_dark_outline = Color(0xFF9E8D82)
    val md_theme_dark_inverseOnSurface = Color(0xFF201A17)
    val md_theme_dark_inverseSurface = Color(0xFFECE0DA)
    val md_theme_dark_inversePrimary = Color(0xFF914C00)
    val md_theme_dark_shadow = Color(0xFF000000)
    val md_theme_dark_surfaceTint = Color(0xFFFFB77F)
    val md_theme_dark_outlineVariant = Color(0xFF51443B)
    val md_theme_dark_scrim = Color(0xFF000000)

    val seed = Color(0xFFFF8A00)
    val CustomColor1 = Color(0xFFFFAB6C)
    val light_CustomColor1 = Color(0xFF944B00)
    val light_onCustomColor1 = Color(0xFFFFFFFF)
    val light_CustomColor1Container = Color(0xFFFFDCC5)
    val light_onCustomColor1Container = Color(0xFF301400)
    val dark_CustomColor1 = Color(0xFFFFB783)
    val dark_onCustomColor1 = Color(0xFF4F2500)
    val dark_CustomColor1Container = Color(0xFF703700)
    val dark_onCustomColor1Container = Color(0xFFFFDCC5)

    val LightColors = lightColorScheme(
        primary = md_theme_light_primary,
        onPrimary = md_theme_light_onPrimary,
        primaryContainer = md_theme_light_primaryContainer,
        onPrimaryContainer = md_theme_light_onPrimaryContainer,
        secondary = md_theme_light_secondary,
        onSecondary = md_theme_light_onSecondary,
        secondaryContainer = md_theme_light_secondaryContainer,
        onSecondaryContainer = md_theme_light_onSecondaryContainer,
        tertiary = md_theme_light_tertiary,
        onTertiary = md_theme_light_onTertiary,
        tertiaryContainer = md_theme_light_tertiaryContainer,
        onTertiaryContainer = md_theme_light_onTertiaryContainer,
        error = md_theme_light_error,
        errorContainer = md_theme_light_errorContainer,
        onError = md_theme_light_onError,
        onErrorContainer = md_theme_light_onErrorContainer,
        background = md_theme_light_background,
        onBackground = md_theme_light_onBackground,
        surface = md_theme_light_surface,
        onSurface = md_theme_light_onSurface,
        surfaceVariant = md_theme_light_surfaceVariant,
        onSurfaceVariant = md_theme_light_onSurfaceVariant,
        outline = md_theme_light_outline,
        inverseOnSurface = md_theme_light_inverseOnSurface,
        inverseSurface = md_theme_light_inverseSurface,
        inversePrimary = md_theme_light_inversePrimary,
        surfaceTint = md_theme_light_surfaceTint,
        outlineVariant = md_theme_light_outlineVariant,
        scrim = md_theme_light_scrim,
    )

    val DarkColors = darkColorScheme(
        primary = md_theme_dark_primary,
        onPrimary = md_theme_dark_onPrimary,
        primaryContainer = md_theme_dark_primaryContainer,
        onPrimaryContainer = md_theme_dark_onPrimaryContainer,
        secondary = md_theme_dark_secondary,
        onSecondary = md_theme_dark_onSecondary,
        secondaryContainer = md_theme_dark_secondaryContainer,
        onSecondaryContainer = md_theme_dark_onSecondaryContainer,
        tertiary = md_theme_dark_tertiary,
        onTertiary = md_theme_dark_onTertiary,
        tertiaryContainer = md_theme_dark_tertiaryContainer,
        onTertiaryContainer = md_theme_dark_onTertiaryContainer,
        error = md_theme_dark_error,
        errorContainer = md_theme_dark_errorContainer,
        onError = md_theme_dark_onError,
        onErrorContainer = md_theme_dark_onErrorContainer,
        background = md_theme_dark_background,
        onBackground = md_theme_dark_onBackground,
        surface = md_theme_dark_surface,
        onSurface = md_theme_dark_onSurface,
        surfaceVariant = md_theme_dark_surfaceVariant,
        onSurfaceVariant = md_theme_dark_onSurfaceVariant,
        outline = md_theme_dark_outline,
        inverseOnSurface = md_theme_dark_inverseOnSurface,
        inverseSurface = md_theme_dark_inverseSurface,
        inversePrimary = md_theme_dark_inversePrimary,
        surfaceTint = md_theme_dark_surfaceTint,
        outlineVariant = md_theme_dark_outlineVariant,
        scrim = md_theme_dark_scrim,
    )

}