/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
    model: AccountModel = viewModel()
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
                style = MaterialTheme.typography.body2.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Owner
        if (owner != null) {
            Text(stringResource(R.string.collection_properties_owner), style = MaterialTheme.typography.h5)
            Text(
                text = owner,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Last synced (for all applicable authorities)
        Text(stringResource(R.string.collection_properties_sync_time), style = MaterialTheme.typography.h5)
        if (lastSynced.isEmpty())
            Text(
                stringResource(R.string.collection_properties_sync_time_never),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        else
            for ((app, timestamp) in lastSynced.entries) {
                Text(
                    text = app,
                    style = MaterialTheme.typography.body2
                )

                val timeStr = DateUtils.getRelativeDateTimeString(
                    context, timestamp, DateUtils.SECOND_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0
                ).toString()
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        Spacer(Modifier.height(8.dp))

        if (collection.supportsWebPush) {
            collection.pushTopic?.let { topic ->
                Text(
                    stringResource(R.string.collection_properties_push_support),
                    style = MaterialTheme.typography.h5
                )
                Text(
                    stringResource(R.string.collection_properties_push_support_web_push),
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            val subscribedStr =
                collection.pushSubscriptionCreated?.let { timestamp ->
                    val timeStr = DateUtils.getRelativeDateTimeString(
                        context, timestamp, DateUtils.SECOND_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0
                    ).toString()
                    stringResource(R.string.collection_properties_push_subscribed_at, timeStr)
                } ?: stringResource(R.string.collection_properties_push_subscribed_never)
            Text(
                text = subscribedStr,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.padding(bottom = 16.dp)
            )
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
            description = "Description",
            supportsWebPush = true,
            pushTopic = "push-topic"
        ),
        owner = "Owner",
        lastSynced = emptyMap()
    )
}