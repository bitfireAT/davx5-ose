package at.bitfire.davdroid.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val BadgesIcons.Badge1UpExtralife: ImageVector
    get() {
        if (_Badge1UpExtralife != null) {
            return _Badge1UpExtralife!!
        }
        _Badge1UpExtralife = ImageVector.Builder(
            name = "Badge1UpExtralife",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = true
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(10f, 19f)
                verticalLineTo(19f)
                curveTo(9.4f, 19f, 9f, 18.6f, 9f, 18f)
                verticalLineTo(17f)
                curveTo(9f, 16.5f, 9.4f, 16f, 10f, 16f)
                verticalLineTo(16f)
                curveTo(10.5f, 16f, 11f, 16.4f, 11f, 17f)
                verticalLineTo(18f)
                curveTo(11f, 18.6f, 10.6f, 19f, 10f, 19f)
                moveTo(15f, 18f)
                verticalLineTo(17f)
                curveTo(15f, 16.5f, 14.6f, 16f, 14f, 16f)
                verticalLineTo(16f)
                curveTo(13.5f, 16f, 13f, 16.4f, 13f, 17f)
                verticalLineTo(18f)
                curveTo(13f, 18.5f, 13.4f, 19f, 14f, 19f)
                verticalLineTo(19f)
                curveTo(14.6f, 19f, 15f, 18.6f, 15f, 18f)
                moveTo(22f, 12f)
                curveTo(22f, 14.6f, 20.4f, 16.9f, 18f, 18.4f)
                verticalLineTo(20f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16f, 22f)
                horizontalLineTo(8f)
                arcTo(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 6f, 20f)
                verticalLineTo(18.4f)
                curveTo(3.6f, 16.9f, 2f, 14.6f, 2f, 12f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 2f)
                arcTo(10f, 10f, 0f, isMoreThanHalf = false, isPositiveArc = true, 22f, 12f)
                moveTo(7f, 10f)
                curveTo(7f, 8.9f, 6.4f, 7.9f, 5.5f, 7.4f)
                curveTo(4.5f, 8.7f, 4f, 10.3f, 4f, 12f)
                curveTo(4f, 12.3f, 4f, 12.7f, 4.1f, 13f)
                curveTo(5.7f, 12.9f, 7f, 11.6f, 7f, 10f)
                moveTo(9f, 9f)
                curveTo(9f, 10.7f, 10.3f, 12f, 12f, 12f)
                curveTo(13.7f, 12f, 15f, 10.7f, 15f, 9f)
                curveTo(15f, 7.3f, 13.7f, 6f, 12f, 6f)
                curveTo(10.3f, 6f, 9f, 7.3f, 9f, 9f)
                moveTo(16f, 20f)
                verticalLineTo(15.5f)
                curveTo(14.8f, 15.2f, 13.4f, 15f, 12f, 15f)
                curveTo(10.6f, 15f, 9.2f, 15.2f, 8f, 15.5f)
                verticalLineTo(20f)
                horizontalLineTo(16f)
                moveTo(19.9f, 13f)
                curveTo(20f, 12.7f, 20f, 12.3f, 20f, 12f)
                curveTo(20f, 10.3f, 19.5f, 8.7f, 18.5f, 7.4f)
                curveTo(17.6f, 7.9f, 17f, 8.9f, 17f, 10f)
                curveTo(17f, 11.6f, 18.3f, 12.9f, 19.9f, 13f)
                close()
            }
        }.build()

        return _Badge1UpExtralife!!
    }

@Suppress("ObjectPropertyName")
private var _Badge1UpExtralife: ImageVector? = null
