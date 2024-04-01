/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.ui.widget.ExceptionInfoDialog
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.jvm.optionals.getOrNull

@Composable
fun DeleteCollectionDialog(
    collection: Collection,
    onDismiss: () -> Unit,
    model: AccountModel = viewModel()
) {
    var started by remember { mutableStateOf(false) }
    val result by model.deleteCollectionResult.observeAsState()

    fun dismiss() {
        // dismiss dialog, reset data so that it can be shown from start again
        onDismiss()
        started = false
        model.deleteCollectionResult.value = null
    }

    if (result?.isEmpty == true) {
        // finished without error
        dismiss()
        return
    }

    Dialog(
        properties = DialogProperties(
            dismissOnClickOutside =
                result != null ||       // finished with error message
                !started                // not started
            ),
        onDismissRequest = ::dismiss
    ) {
        Card {
            DeleteCollectionDialog_Content(
                collection = collection,
                started = started,
                result = result?.getOrNull(),
                onDeleteCollection = {
                    started = true
                    model.deleteCollection(collection)
                },
                onCancel = ::dismiss
            )
        }
    }
}

@Composable
fun DeleteCollectionDialog_Content(
    collection: Collection,
    started: Boolean,
    result: Exception?,
    onDeleteCollection: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    Column(Modifier.padding(16.dp)) {
        Text(
            stringResource(R.string.delete_collection_confirm_title),
            style = MaterialTheme.typography.h6
        )

        Text(
            stringResource(R.string.delete_collection_confirm_warning, collection.title()),
            style = MaterialTheme.typography.body1,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        when {
            result != null -> ExceptionInfoDialog(
                exception = result,
                remoteResource = collection.url,
                onDismiss = onCancel
            )

            started -> {
                LinearProgressIndicator(
                    color = MaterialTheme.colors.secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }

            else ->
                DeleteCollectionDialog_Confirmation(
                    onDeleteCollection = onDeleteCollection,
                    onCancel = onCancel
                )
        }

    }
}

@Composable
fun DeleteCollectionDialog_Confirmation(
    onDeleteCollection: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    var confirmed by remember { mutableStateOf(false) }
    Row(Modifier.padding(vertical = 8.dp)) {
        Checkbox(
            checked = confirmed,
            onCheckedChange = {
                confirmed = !confirmed
            }
        )
        Text(
            stringResource(R.string.delete_collection_data_shall_be_deleted),
            style = MaterialTheme.typography.body1,
            modifier = Modifier.clickable { confirmed = !confirmed }
        )
    }

    Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth()
    ) {
        TextButton(
            onClick = onCancel
        ) {
            Text(stringResource(android.R.string.cancel).uppercase())
        }

        TextButton(
            onClick = onDeleteCollection,
            enabled = confirmed
        ) {
            Text(stringResource(R.string.delete_collection).uppercase())
        }
    }
}

@Composable
@Preview
fun DeleteCollectionDialog_Preview() {
    DeleteCollectionDialog_Content(
        collection = Collection(
            type = Collection.TYPE_CALENDAR,
            url = "https://example.com/calendar".toHttpUrl(),
        ),
        started = false,
        result = null
    )
}

@Composable
@Preview
fun DeleteCollectionDialog_Preview_Deleting() {
    DeleteCollectionDialog_Content(
        collection = Collection(
            type = Collection.TYPE_CALENDAR,
            url = "https://example.com/calendar".toHttpUrl(),
        ),
        started = true,
        result = null
    )
}

@Composable
@Preview
fun DeleteCollectionDialog_Preview_Error() {
    DeleteCollectionDialog_Content(
        collection = Collection(
            type = Collection.TYPE_CALENDAR,
            url = "https://example.com/calendar".toHttpUrl(),
        ),
        started = true,
        result = Exception("Test error")
    )
}