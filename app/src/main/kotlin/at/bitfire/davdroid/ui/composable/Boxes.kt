/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun PixelBoxes(
    colors: Array<Color>,
    modifier: Modifier = Modifier
) {
    Row(modifier) {
        for (color in colors)
            Box(Modifier.padding(4.dp)) {
                Box(Modifier
                    .background(color)
                    .size(12.dp))
            }
    }
}

@Composable
@Preview
fun PixelBoxes_Sample() {
    PixelBoxes(arrayOf(Color.Magenta, Color.Yellow, Color.Cyan))
}