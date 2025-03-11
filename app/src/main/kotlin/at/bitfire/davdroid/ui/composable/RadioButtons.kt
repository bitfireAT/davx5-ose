/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.bitfire.davdroid.ui.AppTheme

@Composable
fun RadioButtons(
    options: List<String> = listOf<String>(),
    initiallySelectedIdx: Int? = null,
    onOptionSelected: (Int) -> Unit = { _ -> },
    optionTextPadding: PaddingValues = PaddingValues(10.dp),
    modifier: Modifier = Modifier
) {
    var selectedIdx by remember { mutableStateOf(initiallySelectedIdx) }
    Column(
        // Modifier.selectableGroup() is essential to ensure correct accessibility behavior
        modifier = modifier.selectableGroup()
    ) {
        options.forEachIndexed { idx, text ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = (selectedIdx == idx),
                        onClick = {
                            selectedIdx = idx
                            onOptionSelected(idx)
                        },
                        role = Role.Companion.RadioButton
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (idx == selectedIdx),
                    onClick = null, // null recommended for accessibility with screen readers
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(optionTextPadding)
                )
            }
        }
    }
}

@Preview
@Composable
private fun RadioButtons_Preview_NoInitialSelection() {
    AppTheme {
        RadioButtons(
            options = listOf(
                "Option 1",
                "Option 2 is the longest of all the options, so we can see whether line breaks are not a problem.",
                "Option 3")
        )
    }
}

@Preview
@Composable
private fun RadioButtons_Preview_InitialSelection() {
    AppTheme {
        RadioButtons(
            options = listOf(
                "Option 1",
                "Option 2",
                "Option 3"
            ),
            initiallySelectedIdx = 1
        )
    }
}