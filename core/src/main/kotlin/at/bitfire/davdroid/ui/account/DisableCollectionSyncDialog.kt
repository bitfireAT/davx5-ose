/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.composable.AppTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pending request to turn off synchronization of a collection, waiting for user confirmation.
 */
data class DisableCollectionSyncRequest(
    val collectionId: Long,
    val title: String,
    val unsyncedChanges: Int? = null
)

/**
 * Asks the user to confirm turning off synchronization of a collection, which removes its local data.
 *
 * @param title             title of the collection
 * @param unsyncedChanges   number of local changes that will be lost (*null* while still counting)
 * @param progressDelay     how long to count before showing progress bar (avoid flicker when counting is fast)
 */
@Composable
fun DisableCollectionSyncDialog(
    title: String,
    unsyncedChanges: Int?,
    progressDelay: Duration = 300.milliseconds,
    onConfirm: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    var showProgress by remember { mutableStateOf(progressDelay <= Duration.ZERO) }
    LaunchedEffect(progressDelay) {
        delay(progressDelay)
        showProgress = true
    }

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

                when {
                    unsyncedChanges == null ->
                        if (showProgress)
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp)
                            )

                    unsyncedChanges > 0 ->
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
fun DisableCollectionSyncDialog_Preview_Counting() {
    AppTheme {
        DisableCollectionSyncDialog(
            title = "Sample Calendar",
            unsyncedChanges = null,
            progressDelay = Duration.ZERO
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
