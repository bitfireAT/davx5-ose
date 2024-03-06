package at.bitfire.davdroid.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Today
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
    onChangeSync: (id: Long, sync: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
        modifier = modifier
    ) {
        items(
            count = collections.itemCount,
            key = collections.itemKey()
        ) { index ->
            collections[index]?.let { item ->
                CollectionListItem(
                    item,
                    onChangeSync = { sync ->
                        onChangeSync(item.id, sync)
                    }
                )
            }
        }
    }
}

@Composable
fun CollectionListItem(
    collection: Collection,
    onChangeSync: (sync: Boolean) -> Unit = {}
) {
    var showPropertiesDialog by remember { mutableStateOf(false) }
    if (showPropertiesDialog)
        CollectionPropertiesDialog(
            collection = collection,
            onDismiss = { showPropertiesDialog = false }
        )

    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = collection.sync,
            onCheckedChange = onChangeSync,
            modifier = Modifier.padding(end = 8.dp)
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
            if (collection.readOnly())
                Icon(Icons.Default.RemoveCircle, null)
            if (collection.supportsVEVENT == true)
                Icon(Icons.Default.Today, null)
            if (collection.supportsVTODO == true)
                Icon(Icons.Default.PlaylistAddCheck, null)
            if (collection.supportsVJOURNAL == true)
                Icon(Icons.Default.TextSnippet, null)

            var showOverflow by remember { mutableStateOf(false) }
            IconButton(onClick = { showOverflow = true }) {
                Icon(Icons.Default.MoreVert, null)
            }
            DropdownMenu(
                expanded = showOverflow,
                onDismissRequest = { showOverflow = false }
            ) {
                DropdownMenuItem(onClick = {
                    showPropertiesDialog = true
                    showOverflow = false
                }) {
                    Text(stringResource(R.string.collection_properties))
                }
            }
        }
    }
}

@Composable
@Preview
fun CollectionsList_Sample_AddressBook() {
    CollectionListItem(
        Collection(
            type = Collection.TYPE_ADDRESSBOOK,
            url = "https://example.com/carddav/addressbook".toHttpUrl(),
            displayName = "Sample Address Book",
            description = "This Sample Address Book even has some lengthy description."
        )
    )
}