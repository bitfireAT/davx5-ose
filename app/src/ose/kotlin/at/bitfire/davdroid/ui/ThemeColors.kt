package at.bitfire.davdroid.ui

import androidx.compose.material.Colors
import androidx.compose.ui.graphics.Color

object ThemeColors {

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