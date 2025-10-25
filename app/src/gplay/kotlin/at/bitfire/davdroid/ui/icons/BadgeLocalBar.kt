/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val BadgesIcons.BadgeLocalBar: ImageVector
    get() {
        if (_BadgeLocalBar != null) {
            return _BadgeLocalBar!!
        }
        _BadgeLocalBar = ImageVector.Builder(
            name = "BadgeLocalBar",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(21f, 5f)
                verticalLineTo(3f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                lineToRelative(8f, 9f)
                verticalLineToRelative(5f)
                horizontalLineTo(6f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(12f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-5f)
                verticalLineToRelative(-5f)
                lineToRelative(8f, -9f)
                close()
                moveTo(7.43f, 7f)
                lineTo(5.66f, 5f)
                horizontalLineToRelative(12.69f)
                lineToRelative(-1.78f, 2f)
                horizontalLineTo(7.43f)
                close()
            }
        }.build()

        return _BadgeLocalBar!!
    }

@Suppress("ObjectPropertyName")
private var _BadgeLocalBar: ImageVector? = null
