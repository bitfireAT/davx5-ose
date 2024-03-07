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
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.log.Logger

@Composable
fun DeleteCollectionDialog(
    collection: Collection,
    onDismiss: () -> Unit,
    model: AccountModel = viewModel()
) {
    val result by model.deleteCollectionResult.observeAsState()
    result?.let { optionalResult ->
        onDismiss()
        if (optionalResult.isPresent) {
            // TODO exception
            Logger.log.warning("ERROR")
        }
    }

    var deleting by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Card {
            DeleteCollectionDialog_Content(
                name = collection.title(),
                onDeleteCollection = {
                    deleting = true
                    model.deleteCollection(collection)
                },
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
fun DeleteCollectionDialog_Content(
    name: String,
    isDeleting: Boolean = false,
    onDeleteCollection: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    Column(Modifier.padding(16.dp)) {
        Text(
            stringResource(R.string.delete_collection_confirm_title),
            style = MaterialTheme.typography.h6
        )

        Text(
            stringResource(R.string.delete_collection_confirm_warning, name),
            style = MaterialTheme.typography.body1,
            modifier = Modifier.padding(top = 8.dp)
        )

        if (isDeleting)
            LinearProgressIndicator(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        else {
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
                    onClick = onDismiss
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
    }
}

@Composable
@Preview
fun DeleteCollectionDialog_Preview() {
    DeleteCollectionDialog_Content(
        "Test Calendar"
    )
}

@Composable
@Preview
fun DeleteCollectionDialog_Preview_Deleting() {
    DeleteCollectionDialog_Content(
        "Test Calendar",
        isDeleting = true
    )
}