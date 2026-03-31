/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val FolderMatch: ImageVector
    get() {
        if (_FolderMatch != null) {
            return _FolderMatch!!
        }
        _FolderMatch = ImageVector.Builder(
            name = "FolderMatch24Dp000000FILL0Wght400GRAD0Opsz24",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(360f, 776f)
                quadToRelative(-81f, -32f, -133.5f, -100.5f)
                reflectiveQuadTo(163f, 520f)
                horizontalLineToRelative(81f)
                quadToRelative(9f, 53f, 39f, 97f)
                reflectiveQuadToRelative(77f, 71f)
                verticalLineToRelative(88f)
                close()
                moveTo(500f, 880f)
                quadToRelative(-25f, 0f, -42.5f, -17.5f)
                reflectiveQuadTo(440f, 820f)
                verticalLineToRelative(-240f)
                quadToRelative(0f, -25f, 17.5f, -42.5f)
                reflectiveQuadTo(500f, 520f)
                horizontalLineToRelative(88f)
                quadToRelative(15f, 0f, 28.5f, 7f)
                reflectiveQuadToRelative(21.5f, 20f)
                lineToRelative(22f, 33f)
                horizontalLineToRelative(160f)
                quadToRelative(25f, 0f, 42.5f, 17.5f)
                reflectiveQuadTo(880f, 640f)
                verticalLineToRelative(180f)
                quadToRelative(0f, 25f, -17.5f, 42.5f)
                reflectiveQuadTo(820f, 880f)
                lineTo(500f, 880f)
                close()
                moveTo(140f, 440f)
                quadToRelative(-25f, 0f, -42.5f, -17.5f)
                reflectiveQuadTo(80f, 380f)
                verticalLineToRelative(-240f)
                quadToRelative(0f, -25f, 17.5f, -42.5f)
                reflectiveQuadTo(140f, 80f)
                horizontalLineToRelative(88f)
                quadToRelative(15f, 0f, 28.5f, 7f)
                reflectiveQuadToRelative(21.5f, 20f)
                lineToRelative(22f, 33f)
                horizontalLineToRelative(160f)
                quadToRelative(25f, 0f, 42.5f, 17.5f)
                reflectiveQuadTo(520f, 200f)
                verticalLineToRelative(180f)
                quadToRelative(0f, 25f, -17.5f, 42.5f)
                reflectiveQuadTo(460f, 440f)
                lineTo(140f, 440f)
                close()
                moveTo(720f, 480f)
                quadToRelative(0f, -65f, -32f, -120.5f)
                reflectiveQuadTo(600f, 272f)
                verticalLineToRelative(-88f)
                quadToRelative(91f, 37f, 145.5f, 117.5f)
                reflectiveQuadTo(800f, 480f)
                horizontalLineToRelative(-80f)
                close()
                moveTo(520f, 800f)
                horizontalLineToRelative(280f)
                verticalLineToRelative(-140f)
                lineTo(617f, 660f)
                lineToRelative(-40f, -60f)
                horizontalLineToRelative(-57f)
                verticalLineToRelative(200f)
                close()
                moveTo(160f, 360f)
                horizontalLineToRelative(280f)
                verticalLineToRelative(-140f)
                lineTo(257f, 220f)
                lineToRelative(-40f, -60f)
                horizontalLineToRelative(-57f)
                verticalLineToRelative(200f)
                close()
                moveTo(520f, 800f)
                verticalLineToRelative(-200f)
                verticalLineToRelative(200f)
                close()
                moveTo(160f, 360f)
                verticalLineToRelative(-200f)
                verticalLineToRelative(200f)
                close()
            }
        }.build()

        return _FolderMatch!!
    }

@Suppress("ObjectPropertyName")
private var _FolderMatch: ImageVector? = null
