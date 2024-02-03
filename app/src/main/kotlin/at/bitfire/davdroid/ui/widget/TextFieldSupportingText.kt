package at.bitfire.davdroid.ui.widget

import androidx.compose.animation.AnimatedContent
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Placed in a `Column` below a `TextField`.
 * Serves as a replacement of the `app:helperText` for XML, or
 * [`supportingText`](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#TextField(kotlin.String,kotlin.Function1,androidx.compose.ui.Modifier,kotlin.Boolean,kotlin.Boolean,androidx.compose.ui.text.TextStyle,kotlin.Function0,kotlin.Function0,kotlin.Function0,kotlin.Function0,kotlin.Function0,kotlin.Function0,kotlin.Function0,kotlin.Boolean,androidx.compose.ui.text.input.VisualTransformation,androidx.compose.foundation.text.KeyboardOptions,androidx.compose.foundation.text.KeyboardActions,kotlin.Boolean,kotlin.Int,kotlin.Int,androidx.compose.foundation.interaction.MutableInteractionSource,androidx.compose.ui.graphics.Shape,androidx.compose.material3.TextFieldColors))
 * of Material 3.
 *
 * Displays a small caption label which gives more information about the field.
 * May be used for displaying help text, or errors, for example.
 *
 * @param text The text to display, if null, the component will be hidden and won't use any space.
 * @param modifier Any modifiers to apply to the component.
 * @param isError If true, the text will be displayed in red.
 */
@Composable
fun TextFieldSupportingText(text: String?, modifier: Modifier = Modifier, isError: Boolean = false) {
    AnimatedContent(
        targetState = text,
        label = "animate supporting text visibility"
    ) { value ->
        value?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.caption,
                modifier = modifier,
                color = MaterialTheme.colors.error.takeIf { isError } ?: Color.Unspecified
            )
        }
    }
}
