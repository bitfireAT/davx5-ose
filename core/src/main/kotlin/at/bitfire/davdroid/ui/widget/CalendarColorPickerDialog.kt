/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlexBox
import androidx.compose.foundation.layout.FlexDirection
import androidx.compose.foundation.layout.FlexWrap
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import at.bitfire.synctools.icalendar.Css3Color

@Composable
@Preview
fun CalendarColorPickerDialog(
    onSelectColor: (color: Int) -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val colors = remember {
        Css3Color.entries.sortedBy { css3Color ->
            Color(css3Color.argb).luminance()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.verticalScroll(rememberScrollState())) {
            FlexBox(
                modifier = Modifier.padding(8.dp),
                config = {
                    direction(FlexDirection.Row)
                    gap(2.dp)
                    wrap(FlexWrap.Wrap)
                }
            ) {
                for (color in colors) {
                    Box(Modifier
                        .background(color = Color(color.argb), shape = CircleShape)
                        .clickable { onSelectColor(color.argb) }
                        .size(32.dp)
                        .padding(8.dp)
                        .semantics {
                            contentDescription = color.name
                        }
                    )
                }
            }
        }
    }
}