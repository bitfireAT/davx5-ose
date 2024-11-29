package at.bitfire.davdroid.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val BadgesIcons.BadgeCoffee: ImageVector
    get() {
        if (_BadgeCoffee != null) {
            return _BadgeCoffee!!
        }
        _BadgeCoffee = ImageVector.Builder(
            name = "BadgeCoffee",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = true
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(20f, 3f)
                lineTo(4f, 3f)
                verticalLineToRelative(10f)
                curveToRelative(0f, 2.21f, 1.79f, 4f, 4f, 4f)
                horizontalLineToRelative(6f)
                curveToRelative(2.21f, 0f, 4f, -1.79f, 4f, -4f)
                verticalLineToRelative(-3f)
                horizontalLineToRelative(2f)
                curveToRelative(1.11f, 0f, 2f, -0.9f, 2f, -2f)
                lineTo(22f, 5f)
                curveToRelative(0f, -1.11f, -0.89f, -2f, -2f, -2f)
                close()
                moveTo(20f, 8f)
                horizontalLineToRelative(-2f)
                lineTo(18f, 5f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(3f)
                close()
                moveTo(4f, 19f)
                horizontalLineToRelative(16f)
                verticalLineToRelative(2f)
                lineTo(4f, 21f)
                close()
            }
        }.build()

        return _BadgeCoffee!!
    }

@Suppress("ObjectPropertyName")
private var _BadgeCoffee: ImageVector? = null
