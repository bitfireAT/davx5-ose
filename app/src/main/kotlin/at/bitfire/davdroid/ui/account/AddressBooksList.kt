package at.bitfire.davdroid.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import at.bitfire.davdroid.db.Collection
import okhttp3.HttpUrl.Companion.toHttpUrl

@Composable
fun AddressBooksList(
    collections: LazyPagingItems<Collection>,
    onChangeSync: (id: Long, sync: Boolean) -> Unit
) {
    val listState: LazyListState = rememberLazyListState()

    Column {
        LinearProgressIndicator(
            color = MaterialTheme.colors.secondary,
            modifier = Modifier.fillMaxWidth()
        )

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top)
        ) {

            items(
                count = collections.itemCount,
                key = collections.itemKey()
            ) { index ->
                collections[index]?.let { item ->
                    AddressBook(
                        item,
                        onChangeSync = { sync ->
                            onChangeSync(item.id, sync)
                        }
                    )
                }
            }
        }
    }

}

@Composable
fun AddressBook(
    collection: Collection,
    onChangeSync: (sync: Boolean) -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
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

        if (collection.readOnly())
            Icon(Icons.Default.DoNotDisturbOn, null)

        IconButton(
            onClick = {},
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Icon(Icons.Default.MoreVert, null)
        }
    }
}

@Composable
@Preview
fun AddressBook_Sample() {
    AddressBook(
        Collection(
            type = Collection.TYPE_ADDRESSBOOK,
            url = "https://example.com/carddav/addressbook".toHttpUrl(),
            displayName = "Sample Address Book",
            description = "This Sample Address Book even has some lengthy description."
        )
    )
}