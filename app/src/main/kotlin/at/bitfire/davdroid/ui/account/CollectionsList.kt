package at.bitfire.davdroid.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Checkbox
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.Task
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import okhttp3.HttpUrl.Companion.toHttpUrl

@Composable
fun CollectionsList(
    collections: LazyPagingItems<Collection>,
    onChangeSync: (collectionId: Long, sync: Boolean) -> Unit,
    onChangeForceReadOnly: (collectionId: Long, forceReadOnly: Boolean) -> Unit,
    onSubscribe: (collection: Collection) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
        modifier = modifier
    ) {
        items(
            count = collections.itemCount,
            key = collections.itemKey()
        ) { index ->
            collections[index]?.let { item ->
                if (item.type == Collection.TYPE_WEBCAL)
                    CollectionList_Subscription(
                        item,
                        onSubscribe = {
                            onSubscribe(item)
                        }
                    )
                else
                    CollectionList_Item(
                        item,
                        onChangeSync = { sync ->
                            onChangeSync(item.id, sync)
                        },
                        onChangeForceReadOnly = { forceReadOnly ->
                            onChangeForceReadOnly(item.id, forceReadOnly)
                        }
                    )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectionList_Item(
    collection: Collection,
    onChangeSync: (sync: Boolean) -> Unit = {},
    onChangeForceReadOnly: (forceReadOnly: Boolean) -> Unit = {}
) {
    val context = LocalContext.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(IntrinsicSize.Min)
    ) {
        if (collection.type == Collection.TYPE_CALENDAR) {
            val color = collection.color?.let { Color(it) } ?: Color.Transparent
            Box(
                Modifier
                    .background(color)
                    .fillMaxHeight()
                    .width(4.dp)
            )
        }

        Switch(
            checked = collection.sync,
            onCheckedChange = onChangeSync,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .semantics {
                    contentDescription = context.getString(R.string.account_synchronize_this_collection)
                }
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                collection.title(),
                style = MaterialTheme.typography.body1
            )
            collection.description?.let { description ->
                Text(
                    description,
                    style = MaterialTheme.typography.body2
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            FlowRow(
                verticalArrangement = Arrangement.Center,
                horizontalArrangement = Arrangement.End,
                maxItemsInEachRow = 2,
                modifier = Modifier.fillMaxHeight()
            ) {
                if (collection.readOnly())
                    Icon(Icons.Default.RemoveCircle, stringResource(R.string.account_read_only))
                if (collection.supportsVEVENT == true)
                    Icon(Icons.Default.Today, stringResource(R.string.account_calendar))
                if (collection.supportsVTODO == true)
                    Icon(Icons.Outlined.Task, stringResource(R.string.account_task_list))
                if (collection.supportsVJOURNAL == true)
                    Icon(Icons.AutoMirrored.Default.EventNote, stringResource(R.string.account_journal))
            }

            var showOverflow by remember { mutableStateOf(false) }
            var showPropertiesDialog by remember { mutableStateOf(false) }
            var showDeleteCollectionDialog by remember { mutableStateOf(false) }

            IconButton(onClick = { showOverflow = true }) {
                Icon(Icons.Default.MoreVert, null)
            }
            DropdownMenu(
                expanded = showOverflow,
                onDismissRequest = { showOverflow = false }
            ) {
                // force read-only (only show for collections that are modifiable on the server)
                if (collection.privWriteContent)
                    DropdownMenuItem(
                        onClick = {
                            onChangeForceReadOnly(!collection.forceReadOnly)
                            showOverflow = false
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.collection_force_read_only))
                            Checkbox(
                                checked = collection.readOnly(),
                                onCheckedChange = { forceReadOnly ->
                                    onChangeForceReadOnly(forceReadOnly)
                                    showOverflow = false
                                }
                            )
                        }
                    }

                // show properties
                DropdownMenuItem(onClick = {
                    showPropertiesDialog = true
                    showOverflow = false
                }) {
                    Text(stringResource(R.string.collection_properties))
                }

                // delete collection (only show when required privilege is available)
                if (collection.privUnbind)
                    DropdownMenuItem(onClick = {
                        showDeleteCollectionDialog = true
                        showOverflow = false
                    }) {
                        Text(stringResource(R.string.delete_collection))
                    }
            }

            if (showDeleteCollectionDialog)
                DeleteCollectionDialog(
                    collection = collection,
                    onDismiss = { showDeleteCollectionDialog = false }
                )
            if (showPropertiesDialog)
                CollectionPropertiesDialog(
                    collection = collection,
                    onDismiss = { showPropertiesDialog = false }
                )
        }
    }
}

@Composable
@Preview
fun CollectionsList_Item_Sample() {
    CollectionList_Item(
        Collection(
            type = Collection.TYPE_CALENDAR,
            url = "https://example.com/caldav/sample".toHttpUrl(),
            displayName = "Sample Calendar",
            description = "This Sample Calendar even has some lengthy description.",
            color = 0xffff0000.toInt(),
            forceReadOnly = true,
            supportsVEVENT = true,
            supportsVTODO = true,
            supportsVJOURNAL = true
        )
    )
}

@Composable
fun CollectionList_Subscription(
    item: Collection,
    onSubscribe: () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(IntrinsicSize.Min)
    ) {
        val color = item.color?.let { Color(it) } ?: Color.Transparent
        Box(
            Modifier
                .background(color)
                .fillMaxHeight()
                .width(4.dp)
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .weight(1f)
        ) {
            Text(
                item.title(),
                style = MaterialTheme.typography.body1
            )
            item.description?.let { description ->
                Text(
                    description,
                    style = MaterialTheme.typography.body2
                )
            }
        }

        TextButton(onClick = onSubscribe) {
            Text("Subscribe".uppercase())
        }
    }

}

@Composable
@Preview
fun CollectionList_Subscription_Preview() {
    CollectionList_Subscription(
        Collection(
            type = Collection.TYPE_WEBCAL,
            url = "https://example.com/caldav/sample".toHttpUrl(),
            displayName = "Sample Subscription",
            description = "This Sample Subscription even has some lengthy description.",
            color = 0xffff0000.toInt()
        )
    )
}