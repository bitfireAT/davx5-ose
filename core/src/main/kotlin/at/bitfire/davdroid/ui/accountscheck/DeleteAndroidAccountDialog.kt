/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.accountscheck

import android.text.TextUtils
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.text.HtmlCompat
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.ExternalUris
import at.bitfire.davdroid.ui.UiUtils.toAnnotatedString
import at.bitfire.davdroid.ui.composable.AppTheme

/**
 * Asks the user to confirm deleting an orphaned Android account, which removes its local data.
 */
@Composable
fun DeleteAndroidAccountDialog(
    accountName: String,
    onConfirm: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    AlertDialog(
        icon = {
            Icon(Icons.Default.Delete, contentDescription = null)
        },
        title = {
            Text(stringResource(R.string.accounts_check_delete_title))
        },
        text = {
            // TODO: link to manual page about backups (doesn't exist yet)
            val backupUrl = remember { ExternalUris.Manual.baseUrl.toString() }
            Text(
                HtmlCompat.fromHtml(
                    stringResource(R.string.accounts_check_delete_text, TextUtils.htmlEncode(accountName), backupUrl),
                    HtmlCompat.FROM_HTML_MODE_COMPACT
                ).toAnnotatedString()
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(stringResource(R.string.dialog_delete))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        onDismissRequest = onDismiss
    )
}

@Composable
@Preview
fun DeleteAndroidAccountDialog_Preview() {
    AppTheme {
        DeleteAndroidAccountDialog(
            accountName = "orphan@example.com"
        )
    }
}
