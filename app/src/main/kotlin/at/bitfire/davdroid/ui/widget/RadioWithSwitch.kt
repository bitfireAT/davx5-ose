package at.bitfire.davdroid.ui.widget

import android.content.ActivityNotFoundException
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider

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
 * @param enabled Whether the radio button should be enabled. The enabled state of the switch is
 * reverse from this. So if it's `true`, the switch will be disabled.
 * @param onSelected Gets called whenever the user requests this row to be enabled. Either by
 * selecting the radio button or tapping the text.
 * @param onToggled Gets called whenever the switch gets updated. Contains the checked status.
 */
@Composable
@ExperimentalTextApi
fun RadioWithSwitch(
    title: String,
    summary: AnnotatedString?,
    isSelected: Boolean,
    isToggled: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSelected: () -> Unit,
    onToggled: (Boolean) -> Unit
) {
    val uriHandler = LocalUriHandler.current

    Row(modifier) {
        RadioButton(selected = isSelected, onClick = onSelected, enabled = enabled)

        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = enabled, role = Role.RadioButton, onClick = onSelected)
        ) {
            Text(
                text = title,
                color = LocalContentColor.current.copy(
                    alpha = if (enabled) 1f else ContentAlpha.disabled
                ),
                style = MaterialTheme.typography.body1,
                modifier = Modifier.fillMaxWidth()
            )
            summary?.let { sum ->
                ClickableText(
                    text = sum,
                    style = MaterialTheme.typography.body2.copy(
                        color = LocalContentColor.current.copy(
                            alpha = if (enabled) 1f else ContentAlpha.disabled
                        )
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { index ->
                        // Get the tapped position, and check if there's any link
                        val annotation = sum.getUrlAnnotations(index, index).firstOrNull()
                        try {
                            // If there is, open it
                            annotation?.item?.url?.let(uriHandler::openUri)
                        } catch (_: ActivityNotFoundException) {
                            // There isn't any application available to launch the link
                            Log.e("RadioWithSwitch", "No app available to launch ${annotation?.item?.url}")
                        }
                    }
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
 * @param enabled Whether the radio button should be enabled. The enabled state of the switch is
 * reverse from this. So if it's `true`, the switch will be disabled.
 * @param onSelected Gets called whenever the user requests this row to be enabled. Either by
 * selecting the radio button or tapping the text.
 * @param onToggled Gets called whenever the switch gets updated. Contains the checked status.
 */
@Composable
@ExperimentalTextApi
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
    RadioWithSwitch(
        title,
        buildAnnotatedString { append(summary) },
        isSelected,
        isToggled,
        modifier,
        enabled,
        onSelected,
        onToggled
    )
}

private class PreviewProvider : PreviewParameterProvider<PreviewProvider.PreviewData> {
    data class PreviewData(
        val title: String,
        val summary: String?,
        val isSelected: Boolean,
        val isToggled: Boolean
    )

    override val values: Sequence<PreviewData> = sequenceOf(
        PreviewData("RadioWithSwitch Preview", "An example summary", true, isToggled = false)
    )
}

@Preview
@Composable
@OptIn(ExperimentalTextApi::class)
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
