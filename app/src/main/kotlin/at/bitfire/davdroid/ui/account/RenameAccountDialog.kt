/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.bitfire.davdroid.R

@Composable
fun RenameAccountDialog(
    oldName: String,
    onRenameAccount: (newName: String) -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    var accountName by remember { mutableStateOf(TextFieldValue(oldName, selection = TextRange(oldName.length))) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
        title = { Text(stringResource(R.string.account_rename)) },
        text = { Column {
            Text(
                stringResource(R.string.account_rename_new_name_description),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val focusRequester = remember { FocusRequester() }
            TextField(
                value = accountName,
                onValueChange = { accountName = it },
                label = { Text(stringResource(R.string.account_rename_new_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        onRenameAccount(accountName.text)
                    }
                ),
                modifier = Modifier.focusRequester(focusRequester)
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        }},
        confirmButton = {
            Button(
                onClick = {
                    onRenameAccount(accountName.text)
                },
                enabled = oldName != accountName.text
            ) {
                Text(stringResource(R.string.account_rename_rename))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
@Preview
fun RenameAccountDialog_Preview() {
    RenameAccountDialog("Account Name")
}