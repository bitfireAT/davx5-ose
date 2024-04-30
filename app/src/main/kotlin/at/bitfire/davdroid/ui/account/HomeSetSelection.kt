/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.HomeSet
import okhttp3.HttpUrl.Companion.toHttpUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeSetSelection(
    homeSet: HomeSet?,
    homeSets: List<HomeSet>,
    onSelectHomeSet: (HomeSet) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(modifier) {
        // select first home set if none is selected
        LaunchedEffect(homeSets) {
            if (homeSet == null)
                homeSets.firstOrNull()?.let(onSelectHomeSet)
        }

        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                label = { Text(stringResource(R.string.create_collection_home_set)) },
                value = homeSet?.title() ?: "",
                onValueChange = { /* read-only */ },
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                Column(Modifier.padding(horizontal = 8.dp)
                ) {
                    for (item in homeSets) {
                        Column(
                            modifier = Modifier
                                .clickable(enabled = enabled) {
                                    onSelectHomeSet(item)
                                    expanded = false
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = item.title(),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = item.url.encodedPath,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Preview
fun HomeSetSelection_Preview() {
    val homeSets = listOf(
        HomeSet(
            id = 0,
            serviceId = 0,
            personal = true,
            url = "https://example.com/homeset/first".toHttpUrl()
        ),
        HomeSet(
            id = 0,
            serviceId = 0,
            personal = true,
            url = "https://example.com/homeset/second".toHttpUrl()
        )
    )
    HomeSetSelection(
        homeSet = homeSets.last(),
        homeSets = homeSets,
        onSelectHomeSet = {}
    )
}