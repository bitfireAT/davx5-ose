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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

/*@Composable
fun CollectionPropertiesDialog(
    collection: Collection,
    onDismiss: () -> Unit,
    model: AccountScreenModel = viewModel()
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
        Text(stringResource(R.string.collection_properties_url), style = MaterialTheme.typography.titleSmall)
        SelectionContainer {
            Text(
                collection.url.toString(),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Owner
        if (owner != null) {
            Text(stringResource(R.string.collection_properties_owner), style = MaterialTheme.typography.titleSmall)
            Text(
                text = owner,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Last synced (for all applicable authorities)
        Text(stringResource(R.string.collection_properties_sync_time), style = MaterialTheme.typography.titleSmall)
        if (lastSynced.isEmpty())
            Text(
                stringResource(R.string.collection_properties_sync_time_never),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        else
            for ((app, timestamp) in lastSynced.entries) {
                Text(
                    text = app,
                    style = MaterialTheme.typography.bodyMedium
                )

                val timeStr = DateUtils.getRelativeDateTimeString(
                    context, timestamp, DateUtils.SECOND_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0
                ).toString()
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        Spacer(Modifier.height(8.dp))

        if (collection.supportsWebPush) {
            collection.pushTopic?.let { topic ->
                Text(
                    stringResource(R.string.collection_properties_push_support),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    stringResource(R.string.collection_properties_push_support_web_push),
                    style = MaterialTheme.typography.bodyMedium
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
                style = MaterialTheme.typography.bodyMedium,
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

    fun getCollectionOwner(collection: Collection): LiveData<String?> {
        val id = collection.ownerId ?: return MutableLiveData(null)
        return db.principalDao().getLive(id).map { principal ->
            if (principal == null)
                return@map null
            principal.displayName ?: principal.url.toString()
        }
    }

    fun getCollectionLastSynced(collection: Collection): LiveData<Map<String, Long>> {
        return db.syncStatsDao().getLiveByCollectionId(collection.id).map { syncStatsList ->
            val syncStatsMap = syncStatsList.associateBy { it.authority }
            val interestingAuthorities = listOfNotNull(
                ContactsContract.AUTHORITY,
                CalendarContract.AUTHORITY,
                TaskUtils.currentProvider(context)?.authority
            )
            val result = mutableMapOf<String, Long>()
            for (authority in interestingAuthorities) {
                val lastSync = syncStatsMap[authority]?.lastSync
                if (lastSync != null)
                    result[getAppNameFromAuthority(authority)] = lastSync
            }
            result
        }
    }

    /**
     * Tries to find the application name for given authority. Returns the authority if not
     * found.
     *
     * @param authority authority to find the application name for (ie "at.techbee.jtx")
     * @return the application name of authority (ie "jtx Board")
     */
    private fun getAppNameFromAuthority(authority: String): String {
        val packageManager = context.packageManager
        val packageName = packageManager.resolveContentProvider(authority, 0)?.packageName ?: authority
        return try {
            val appInfo = packageManager.getPackageInfo(packageName, 0).applicationInfo
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            Logger.log.warning("Application name not found for authority: $authority")
            authority
        }
    }


*/