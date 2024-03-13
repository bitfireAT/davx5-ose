package at.bitfire.davdroid.ui.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun EditTextInputDialog(
    title: String,
    initialValue: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueEntered: (String) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    var textValue by remember {
        mutableStateOf(TextFieldValue(
            initialValue ?: "", selection = TextRange(initialValue?.length ?: 0)
        ))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.body1
            )
        },
        text = {
            val focusRequester = remember { FocusRequester() }
            TextField(
                value = textValue,
                onValueChange = { textValue = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        onValueEntered(textValue.text)
                        onDismiss()
                    }
                ),
                modifier = Modifier.focusRequester(focusRequester)
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onValueEntered(textValue.text)
                    onDismiss()
                },
                enabled = textValue.text != initialValue
            ) {
                Text(stringResource(android.R.string.ok).uppercase())
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(stringResource(android.R.string.cancel).uppercase())
            }
        }
    )
}

@Composable
@Preview
fun EditTextInputDialog_Preview() {
    EditTextInputDialog(
        title = "Enter Some Text",
        initialValue = "initial value"
    )
}


@Composable
fun MultipleChoiceInputDialog(
    title: String,
    namesAndValues: List<Pair<String, String>>,
    initialValue: String? = null,
    onValueSelected: (String) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.body1
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for ((name, value) in namesAndValues)
                   Row(verticalAlignment = Alignment.CenterVertically) {
                       RadioButton(
                           selected = value == initialValue,
                           onClick = {
                               onValueSelected(value)
                               onDismiss()
                           }
                       )
                       Text(
                           name,
                           style = MaterialTheme.typography.body1,
                           modifier = Modifier.clickable {
                               onValueSelected(value)
                               onDismiss()
                           }
                       )
                   }
            }
        },
        buttons = {}
    )
}

@Composable
@Preview
fun MultipleChoiceInputDialog_Preview() {
    MultipleChoiceInputDialog(
        title = "Some Title",
        namesAndValues = listOf(
            "Some Name" to "Some Value",
            "Some Other Name" to "Some Other Value"
        )
    )
}