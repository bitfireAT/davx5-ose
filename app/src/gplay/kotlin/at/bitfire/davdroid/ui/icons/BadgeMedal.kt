/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val BadgesIcons.BadgeMedal: ImageVector
    get() {
        if (_BadgeMedal != null) {
            return _BadgeMedal!!
        }
        _BadgeMedal = ImageVector.Builder(
            name = "BadgeMedal",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = true
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(17f, 10.43f)
                verticalLineTo(2f)
                horizontalLineTo(7f)
                verticalLineToRelative(8.43f)
                curveToRelative(0f, 0.35f, 0.18f, 0.68f, 0.49f, 0.86f)
                lineToRelative(4.18f, 2.51f)
                lineToRelative(-0.99f, 2.34f)
                lineToRelative(-3.41f, 0.29f)
                lineToRelative(2.59f, 2.24f)
                lineTo(9.07f, 22f)
                lineTo(12f, 20.23f)
                lineTo(14.93f, 22f)
                lineToRelative(-0.78f, -3.33f)
                lineToRelative(2.59f, -2.24f)
                lineToRelative(-3.41f, -0.29f)
                lineToRelative(-0.99f, -2.34f)
                lineToRelative(4.18f, -2.51f)
                curveTo(16.82f, 11.11f, 17f, 10.79f, 17f, 10.43f)
                close()
                moveTo(13f, 12.23f)
                lineToRelative(-1f, 0.6f)
                lineToRelative(-1f, -0.6f)
                verticalLineTo(3f)
                horizontalLineToRelative(2f)
                verticalLineTo(12.23f)
                close()
            }
        }.build()

        return _BadgeMedal!!
    }

@Suppress("ObjectPropertyName")
private var _BadgeMedal: ImageVector? = null
