package at.bitfire.davdroid.ui.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider

/**
 * Provides a radio button with a text, a switch at the end, and an optional summary to be shown
 * under the main text.
 */
@Composable
fun RadioWithSwitch(
    title: String,
    summary: String?,
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
                style = MaterialTheme.typography.body1,
                modifier = Modifier.fillMaxWidth()
            )
            summary?.let { sum ->
                Text(
                    text = sum,
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Switch(
            checked = isToggled,
            onCheckedChange = onToggled,
            enabled = !enabled
        )
    }
}

private class PreviewProvider : PreviewParameterProvider<PreviewProvider.PreviewData> {
    data class PreviewData(
        val title: String,
        val summary: String?,
        val isSelected: Boolean,
        val isToggled: Boolean
    )

    override val values: Sequence<PreviewData> = sequenceOf(
        PreviewData("RadioWithSwitch Preview", "An example summary", true, isToggled = true),
        PreviewData("RadioWithSwitch Preview", "An example summary", true, isToggled = false),
        PreviewData("RadioWithSwitch Preview", "An example summary", false, isToggled = true),
        PreviewData("RadioWithSwitch Preview", "An example summary", false, isToggled = false),
        PreviewData("RadioWithSwitch Preview", null, true, isToggled = true),
        PreviewData("RadioWithSwitch Preview", null, true, isToggled = false),
        PreviewData("RadioWithSwitch Preview", null, false, isToggled = true),
        PreviewData("RadioWithSwitch Preview", null, false, isToggled = false),
    )
}

@Preview
@Composable
private fun RadioWithSwitch_Preview(
    @PreviewParameter(PreviewProvider::class) data: PreviewProvider.PreviewData
) {
    RadioWithSwitch(
        title = data.title,
        summary = data.summary,
        isSelected = data.isSelected,
        isToggled = data.isToggled,
        onSelected = { },
        onToggled = { }
    )
}
