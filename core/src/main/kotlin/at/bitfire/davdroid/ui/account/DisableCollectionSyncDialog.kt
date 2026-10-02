/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.composable.AppTheme

/**
 * Pending request to turn off synchronization of a collection, waiting for user confirmation.
 */
data class DisableCollectionSyncRequest(
    val collectionId: Long,
    val title: String,
    val unsyncedChanges: Int
)

/**
 * Asks the user to confirm turning off synchronization of a collection, which removes its local data.
 *
 * @param title             title of the collection
 * @param unsyncedChanges   number of local changes that will be lost
 */
@Composable
fun DisableCollectionSyncDialog(
    title: String,
    unsyncedChanges: Int,
    onConfirm: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    AlertDialog(
        icon = {
            Icon(Icons.Default.SyncDisabled, contentDescription = null)
        },
        title = {
            Text(stringResource(R.string.collection_disable_sync_title))
        },
        text = {
            Column {
                Text(stringResource(R.string.collection_disable_sync_warning, title))

                if (unsyncedChanges > 0)
                    Text(
                        text = pluralStringResource(
                            R.plurals.collection_disable_sync_unsynced_changes,
                            unsyncedChanges,
                            unsyncedChanges
                        ),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.collection_disable_sync_confirm))
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
fun DisableCollectionSyncDialog_Preview() {
    AppTheme {
        DisableCollectionSyncDialog(
            title = "Sample Calendar",
            unsyncedChanges = 0
        )
    }
}

@Composable
@Preview
fun DisableCollectionSyncDialog_Preview_UnsyncedChanges() {
    AppTheme {
        DisableCollectionSyncDialog(
            title = "Sample Calendar",
            unsyncedChanges = 3
        )
    }
}
