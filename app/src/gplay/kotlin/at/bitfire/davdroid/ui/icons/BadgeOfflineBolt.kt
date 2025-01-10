/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val BadgesIcons.BadgeOfflineBolt: ImageVector
    get() {
        if (_BadgeOfflineBolt != null) {
            return _BadgeOfflineBolt!!
        }
        _BadgeOfflineBolt = ImageVector.Builder(
            name = "BadgeOfflineBolt",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(12f, 2.02f)
                curveToRelative(-5.51f, 0f, -9.98f, 4.47f, -9.98f, 9.98f)
                reflectiveCurveToRelative(4.47f, 9.98f, 9.98f, 9.98f)
                reflectiveCurveToRelative(9.98f, -4.47f, 9.98f, -9.98f)
                reflectiveCurveTo(17.51f, 2.02f, 12f, 2.02f)
                close()
                moveTo(11.48f, 20f)
                verticalLineToRelative(-6.26f)
                horizontalLineTo(8f)
                lineTo(13f, 4f)
                verticalLineToRelative(6.26f)
                horizontalLineToRelative(3.35f)
                lineTo(11.48f, 20f)
                close()
            }
        }.build()

        return _BadgeOfflineBolt!!
    }

@Suppress("ObjectPropertyName")
private var _BadgeOfflineBolt: ImageVector? = null
