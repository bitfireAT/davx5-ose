/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview

/**
 * Provides a radio button with a text, a switch at the end, and an optional summary to be shown
 * under the main text.
 *
 * @param title The "proper" text of the Radio button. Shown in the middle of the row, between the
 * radio button and the switch.
 * @param summary If not `null`, shown below the title. Used to give more context or information.
 * Supports formatting and interactions.
 * @param isSelected Whether the item is currently selected. Refers to the radio button.
 * @param isToggled Whether the switch is toggled.
 * @param modifier Any modifiers to apply to the row.
 * @param enabled Whether the radio button should be enabled.
 * @param onSelected Gets called whenever the user requests this row to be enabled. Either by
 * selecting the radio button or tapping the text.
 * @param onToggled Gets called whenever the switch gets updated. Contains the checked status.
 */
@Composable
fun RadioWithSwitch(
    title: String,
    summary: (@Composable () -> Unit)?,
    isSelected: Boolean,
    isToggled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSelected: () -> Unit,
    onToggled: (Boolean) -> Unit
) {
    Row(modifier) {
        RadioButton(selected = isSelected, onClick = onSelected, enabled = enabled)

        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = enabled, role = Role.RadioButton, onClick = onSelected)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth()
            )
            summary?.let { sum ->
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    sum()
                }
            }
        }

        Switch(
            checked = isToggled,
            onCheckedChange = onToggled
        )
    }
}

@Preview
@Composable
private fun RadioWithSwitch_Preview() {
    RadioWithSwitch(
        title = "RadioWithSwitch Preview",
        summary = { Text("An example summary") },
        isSelected = true,
        isToggled = false,
        onSelected = { },
        onToggled = { }
    )
}
