package at.bitfire.davdroid.ui.account

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import okhttp3.HttpUrl.Companion.toHttpUrl

@Composable
fun CollectionPropertiesDialog(
    collection: Collection,
    onDismiss: () -> Unit,
    model: AccountActivity2.Model = viewModel()
) {
    val owner by model.getCollectionOwner(collection).observeAsState()
    val lastSynced by model.getCollectionLastSynced(collection).observeAsState(emptyMap())

    Dialog(onDismissRequest = onDismiss) {
        Card {
            CollectionPropertiesContent(
                collection = collection,
                owner = owner,
                lastSynced = lastSynced
            )
        }
    }
}

@Composable
fun CollectionPropertiesContent(
    collection: Collection,
    owner: String?,
    lastSynced: Map<String, Long>
) {
    val context = LocalContext.current

    Column(Modifier.padding(16.dp)) {
        // URL
        Text(stringResource(R.string.collection_properties_url), style = MaterialTheme.typography.h5)
        SelectionContainer {
            Text(
                collection.url.toString(),
                fontFamily = FontFamily.Monospace,
                fontSize = MaterialTheme.typography.body2.fontSize,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Owner
        if (owner != null) {
            Text(stringResource(R.string.collection_properties_owner), style = MaterialTheme.typography.h5)
            Text(owner, Modifier.padding(bottom = 16.dp))
        }

        // Last synced (for all applicable authorities)
        Text(stringResource(R.string.collection_properties_sync_time), style = MaterialTheme.typography.h5)
        if (lastSynced.isEmpty())
            Text(stringResource(R.string.collection_properties_sync_time_never))
        else
            for ((app, timestamp) in lastSynced.entries) {
                Text(app)

                val timeStr = DateUtils.getRelativeDateTimeString(
                    context, timestamp, DateUtils.SECOND_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0
                ).toString()
                Text(timeStr, Modifier.padding(bottom = 8.dp))
            }
    }
}

@Composable
@Preview
fun CollectionPropertiesDialog_Sample() {
    CollectionPropertiesContent(
        collection = Collection(
            id = 1,
            type = Collection.TYPE_ADDRESSBOOK,
            url = "https://example.com".toHttpUrl(),
            displayName = "Display Name",
            description = "Description"
        ),
        owner = "Owner",
        lastSynced = emptyMap()
    )
}