/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.ui.AppTheme
import okhttp3.HttpUrl.Companion.toHttpUrl

@Composable
fun CollectionsList(
    collections: LazyPagingItems<Collection>,
    onChangeSync: (collectionId: Long, sync: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onSubscribe: (collection: Collection) -> Unit = {},
    onCollectionDetails: ((collection: Collection) -> Unit)? = null
) {
    LazyColumn(
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.Top),
        modifier = modifier
    ) {
        items(
            count = collections.itemCount,
            key = collections.itemKey { it.id }
        ) { index ->
            collections[index]?.let { item ->
                if (item.type == Collection.TYPE_WEBCAL)
                    CollectionsList_Item_Webcal(
                        item,
                        onSubscribe = { onSubscribe(item) }
                    )
                else
                    CollectionsList_Item_Standard(
                        item,
                        onChangeSync = { onChangeSync(item.id, it) },
                        onCollectionDetails = onCollectionDetails
                    )
            }
        }

        // make sure we can scroll down far enough so that the last item is not covered by a FAB
        item {
            Spacer(Modifier.height(140.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectionList_Item(
    color: Color? = null,
    title: String,
    description: String? = null,
    addressBook: Boolean = false,
    calendar: Boolean = false,
    todoList: Boolean = false,
    journal: Boolean = false,
    readOnly: Boolean = false,
    onShowDetails: (() -> Unit)? = null,
    syncControl: @Composable () -> Unit
) {
    var modifier = Modifier.fillMaxWidth()
    if (onShowDetails != null)
        modifier = modifier.clickable(onClick = onShowDetails)

    ElevatedCard(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = modifier
    ) {
        Row(Modifier.height(IntrinsicSize.Max)) {
            Box(
                Modifier
                    .background(color ?: Color.Transparent)
                    .width(8.dp)
                    .fillMaxHeight())

            Column(Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))

                        if (description != null)
                            Text(description, style = MaterialTheme.typography.bodyMedium)
                    }

                    syncControl()
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (addressBook)
                        CollectionList_Item_Chip(Icons.Default.Contacts, stringResource(R.string.account_contacts))

                    if (calendar)
                        CollectionList_Item_Chip(Icons.Default.Today, stringResource(R.string.account_calendar))
                    if (todoList)
                        CollectionList_Item_Chip(Icons.Default.Task, stringResource(R.string.account_task_list))
                    if (journal)
                        CollectionList_Item_Chip(Icons.AutoMirrored.Default.EventNote, stringResource(R.string.account_journal))

                    if (readOnly)
                        CollectionList_Item_Chip(Icons.Default.RemoveCircle, stringResource(R.string.account_read_only))
                }
            }
        }
    }
}

@Composable
fun CollectionsList_Item_Standard(
    collection: Collection,
    onChangeSync: (sync: Boolean) -> Unit = {},
    onCollectionDetails: ((collection: Collection) -> Unit)? = null
) {
    CollectionList_Item(
        color = collection.color?.let { Color(it) },
        title = collection.title(),
        description = collection.description,
        addressBook = collection.type == Collection.TYPE_ADDRESSBOOK,
        calendar = collection.supportsVEVENT == true,
        todoList = collection.supportsVTODO == true,
        journal = collection.supportsVJOURNAL == true,
        readOnly = collection.readOnly(),
        onShowDetails = {
            if (onCollectionDetails != null)
                onCollectionDetails(collection)
        }
    ) {
        val context = LocalContext.current
        Switch(
            checked = collection.sync,
            onCheckedChange = onChangeSync,
            modifier = Modifier
                .padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                .semantics {
                    contentDescription = context.getString(R.string.account_synchronize_this_collection)
                }
        )
    }
}

@Composable
@Preview(locale = "de")
fun CollectionsList_Item_Standard_Preview() {
    AppTheme {
        CollectionsList_Item_Standard(
            Collection(
                type = Collection.TYPE_CALENDAR,
                url = "https://example.com/caldav/sample".toHttpUrl(),
                displayName = "Sample Calendar",
                description = "This Sample Calendar even has some lengthy description.",
                color = 0xffff0000.toInt(),
                sync = true,
                forceReadOnly = true,
                supportsVEVENT = true,
                supportsVTODO = true,
                supportsVJOURNAL = true
            )
        )
    }
}

@Composable
fun CollectionsList_Item_Webcal(
    collection: Collection,
    onSubscribe: () -> Unit = {}
) {
    CollectionList_Item(
        color = collection.color?.let { Color(it) },
        title = collection.title(),
        description = collection.description,
        calendar = true,
        readOnly = true
    ) {
        OutlinedButton(
            onClick = onSubscribe,
            modifier = Modifier.padding(start = 4.dp)
        ) {
            Text("Subscribe")
        }
    }
}

@Composable
@Preview
fun CollectionList_Item_Webcal_Preview() {
    AppTheme {
        CollectionsList_Item_Webcal(
            Collection(
                type = Collection.TYPE_WEBCAL,
                url = "https://example.com/caldav/sample".toHttpUrl(),
                displayName = "Sample Subscription",
                description = "This Sample Subscription even has some lengthy description.",
                color = 0xffff0000.toInt()
            )
        )
    }
}

@Composable
fun CollectionList_Item_Chip(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            icon,
            contentDescription = text,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}