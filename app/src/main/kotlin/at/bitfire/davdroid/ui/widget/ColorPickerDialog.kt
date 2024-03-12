package at.bitfire.davdroid.ui.widget

import androidx.compose.runtime.Composable
import at.bitfire.davdroid.Constants
import com.maxkeppeker.sheets.core.models.base.rememberUseCaseState
import com.maxkeppeler.sheets.color.ColorDialog
import com.maxkeppeler.sheets.color.models.ColorConfig
import com.maxkeppeler.sheets.color.models.ColorSelection
import com.maxkeppeler.sheets.color.models.MultipleColors
import com.maxkeppeler.sheets.color.models.SingleColor

@Composable
fun ColorPickerDialog(
    initialColor: Int,
    onSelectColor: (color: Int) -> Unit,
    onDialogDismissed: () -> Unit,
) {
    val templateColors = MultipleColors.ColorsInt(
        Constants.DAVDROID_GREEN_RGBA,
        // 2014 Material Design colors (shade 700)
        0xFFD32F2F.toInt(),
        0xFFC2185B.toInt(),
        0xFF7B1FA2.toInt(),
        0xFF512DA8.toInt(),
        0xFF303F9F.toInt(),
        0xFF1976D2.toInt(),
        0xFF0288D1.toInt(),
        0xFF0097A7.toInt(),
        0xFF00796B.toInt(),
        0xFF388E3C.toInt(),
        0xFF689F38.toInt(),
        0xFFAFB42B.toInt(),
        0xFFFBC02D.toInt(),
        0xFFFFA000.toInt(),
    )

    ColorDialog(
        state = rememberUseCaseState(visible = true, onCloseRequest = { onDialogDismissed() }),
        selection = ColorSelection(
            selectedColor = SingleColor(initialColor),
            onSelectColor = onSelectColor,
        ),
        config = ColorConfig(
            templateColors = templateColors,
            allowCustomColorAlphaValues = false
        ),
    )
}
