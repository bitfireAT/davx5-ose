/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun EditTextInputDialog(
    title: String,
    initialValue: String? = null,
    inputLabel: String? = null,
    passwordField: Boolean = false,
    keyboardType: KeyboardType = if (passwordField) KeyboardType.Password else KeyboardType.Text,
    onValueEntered: (String) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val state = rememberTextFieldState(
        initialText = initialValue ?: "",
        initialSelection = TextRange(initialValue?.length ?: 0)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        text = {
            val focusRequester = remember { FocusRequester() }
            if (passwordField)
                PasswordTextField(
                    password = state,
                    labelText = inputLabel,
                    modifier = Modifier.focusRequester(focusRequester)
                )
            else
                TextField(
                    label = { inputLabel?.let { Text(it) } },
                    state = state,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = ImeAction.Done
                    ),
                    onKeyboardAction = {
                        onValueEntered(state.text.toString())
                        onDismiss()
                    },
                    modifier = Modifier.focusRequester(focusRequester)
                )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onValueEntered(state.text.toString())
                    onDismiss()
                },
                enabled = state.text != initialValue
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
@Preview
fun EditTextInputDialog_Preview() {
    EditTextInputDialog(
        title = "Enter Some Text",
        inputLabel = "Some Label",
        initialValue = "initial value"
    )
}

@Composable
@Preview
fun EditTextInputDialog_Preview_Password() {
    EditTextInputDialog(
        title = "New Password",
        passwordField = true,
        initialValue = "some password"
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
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )

                LazyColumn(Modifier.padding(8.dp)) {
                    items(
                        count = namesAndValues.size,
                        key = { index -> namesAndValues[index].second },
                        itemContent = { index ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val (name, value) = namesAndValues[index]
                                RadioButton(
                                    selected = value == initialValue,
                                    onClick = {
                                        onValueSelected(value)
                                        onDismiss()
                                    }
                                )
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            onValueSelected(value)
                                            onDismiss()
                                        }
                                )
                            }
                        }
                    )
                }
            }
        }
    }
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