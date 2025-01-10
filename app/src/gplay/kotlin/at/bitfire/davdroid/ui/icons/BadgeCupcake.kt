/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val BadgesIcons.BadgeCupcake: ImageVector
    get() {
        if (_BadgeCupcake != null) {
            return _BadgeCupcake!!
        }
        _BadgeCupcake = ImageVector.Builder(
            name = "BadgeCupcake",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(12f, 1.5f)
                arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 14.5f, 4f)
                arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 6.5f)
                arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 9.5f, 4f)
                arcTo(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 1.5f)
                moveTo(15.87f, 5f)
                curveTo(18f, 5f, 20f, 7f, 20f, 9f)
                curveTo(22.7f, 9f, 22.7f, 13f, 20f, 13f)
                horizontalLineTo(4f)
                curveTo(1.3f, 13f, 1.3f, 9f, 4f, 9f)
                curveTo(4f, 7f, 6f, 5f, 8.13f, 5f)
                curveTo(8.57f, 6.73f, 10.14f, 8f, 12f, 8f)
                curveTo(13.86f, 8f, 15.43f, 6.73f, 15.87f, 5f)
                moveTo(5f, 15f)
                horizontalLineTo(8f)
                lineTo(9f, 22f)
                horizontalLineTo(7f)
                lineTo(5f, 15f)
                moveTo(10f, 15f)
                horizontalLineTo(14f)
                lineTo(13f, 22f)
                horizontalLineTo(11f)
                lineTo(10f, 15f)
                moveTo(16f, 15f)
                horizontalLineTo(19f)
                lineTo(17f, 22f)
                horizontalLineTo(15f)
                lineTo(16f, 15f)
                close()
            }
        }.build()

        return _BadgeCupcake!!
    }

@Suppress("ObjectPropertyName")
private var _BadgeCupcake: ImageVector? = null
