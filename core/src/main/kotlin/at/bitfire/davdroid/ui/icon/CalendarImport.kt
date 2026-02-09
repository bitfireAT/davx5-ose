/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.icon

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Icons.Filled.CalendarImport: ImageVector
    get() {
        if (_CalendarImport != null) {
            return _CalendarImport!!
        }
        _CalendarImport = ImageVector.Builder(
            name = "Filled.CalendarImport",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 12f)
                lineTo(8f, 16f)
                horizontalLineTo(11f)
                verticalLineTo(22f)
                horizontalLineTo(13f)
                verticalLineTo(16f)
                horizontalLineTo(16f)
                moveTo(19f, 3f)
                horizontalLineTo(18f)
                verticalLineTo(1f)
                horizontalLineTo(16f)
                verticalLineTo(3f)
                horizontalLineTo(8f)
                verticalLineTo(1f)
                horizontalLineTo(6f)
                verticalLineTo(3f)
                horizontalLineTo(5f)
                curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
                verticalLineTo(19f)
                curveTo(3f, 20.11f, 3.9f, 21f, 5f, 21f)
                horizontalLineTo(9f)
                verticalLineTo(19f)
                horizontalLineTo(5f)
                verticalLineTo(8f)
                horizontalLineTo(19f)
                verticalLineTo(19f)
                horizontalLineTo(15f)
                verticalLineTo(21f)
                horizontalLineTo(19f)
                curveTo(20.11f, 21f, 21f, 20.11f, 21f, 19f)
                verticalLineTo(5f)
                curveTo(21f, 3.9f, 20.11f, 3f, 19f, 3f)
                close()
            }
        }.build()

        return _CalendarImport!!
    }

@Suppress("ObjectPropertyName")
private var _CalendarImport: ImageVector? = null
