/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

/*@Composable
fun DeleteCollectionDialog(
    collection: Collection,
    onDismiss: () -> Unit,
    model: AccountScreenModel = viewModel()
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
}*/