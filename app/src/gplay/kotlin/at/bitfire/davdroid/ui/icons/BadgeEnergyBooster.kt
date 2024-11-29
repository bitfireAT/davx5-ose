package at.bitfire.davdroid.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val BadgesIcons.BadgeEnergyBooster: ImageVector
    get() {
        if (_BadgeEnergyBooster != null) {
            return _BadgeEnergyBooster!!
        }
        _BadgeEnergyBooster = ImageVector.Builder(
            name = "BadgeEnergyBooster",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = true
        ).apply {
            path(fill = SolidColor(Color(0xFF000000))) {
                moveTo(11f, 15f)
                horizontalLineTo(6f)
                lineTo(13f, 1f)
                verticalLineTo(9f)
                horizontalLineTo(18f)
                lineTo(11f, 23f)
                verticalLineTo(15f)
                close()
            }
        }.build()

        return _BadgeEnergyBooster!!
    }

@Suppress("ObjectPropertyName")
private var _BadgeEnergyBooster: ImageVector? = null
