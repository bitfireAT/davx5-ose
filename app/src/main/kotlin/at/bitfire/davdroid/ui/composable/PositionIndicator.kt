package at.bitfire.davdroid.ui.composable

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun PositionIndicator(
    index: Int,
    max: Int,
    modifier: Modifier = Modifier,
    selectedIndicatorColor: Color = MaterialTheme.colorScheme.tertiary,
    unselectedIndicatorColor: Color = contentColorFor(selectedIndicatorColor),
    indicatorSize: Float = 20f,
    indicatorPadding: Float = 20f
) {
    val selectedPosition by animateFloatAsState(
        targetValue = index.toFloat(),
        label = "position"
    )

    Canvas(modifier = modifier) {
        // idx * indicatorSize * 2 + idx * indicatorPadding + indicatorSize
        // idx * (indicatorSize * 2 + indicatorPadding) + indicatorSize
        val padding = indicatorSize * 2 + indicatorPadding

        val totalWidth = indicatorSize * 2 * max + indicatorPadding * (max - 1)
        translate(
            left = size.width / 2 - totalWidth / 2
        ) {
            for (idx in 0 until max) {
                drawCircle(
                    color = unselectedIndicatorColor,
                    radius = indicatorSize,
                    center = Offset(
                        x = idx * padding + indicatorSize,
                        y = size.height / 2
                    )
                )
            }

            drawCircle(
                color = selectedIndicatorColor,
                radius = indicatorSize,
                center = Offset(
                    x = selectedPosition * padding + indicatorSize,
                    y = size.height / 2
                )
            )
        }
    }
}

@Preview(
    showBackground = true,
    backgroundColor = 0xff000000
)
@Composable
fun PositionIndicator_Preview() {
    var index by remember { mutableIntStateOf(0) }

    PositionIndicator(
        index = index,
        max = 5,
        modifier = Modifier
            .width(200.dp)
            .height(50.dp)
            .clickable { if (index == 4) index = 0 else index++ }
    )
}
