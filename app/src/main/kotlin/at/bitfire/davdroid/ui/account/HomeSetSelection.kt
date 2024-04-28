/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.HomeSet
import okhttp3.HttpUrl.Companion.toHttpUrl

@Composable
fun HomeSetSelection(
    homeSet: HomeSet?,
    homeSets: List<HomeSet>,
    onSelectHomeSet: (HomeSet) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        // select first home set if none is selected
        LaunchedEffect(homeSets) {
            if (homeSet == null)
                homeSets.firstOrNull()?.let(onSelectHomeSet)
        }

        Text(
            text = stringResource(R.string.create_collection_home_set),
            style = MaterialTheme.typography.body1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
        for (item in homeSets) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = homeSet == item,
                    onClick = { onSelectHomeSet(item) }
                )
                Column(Modifier
                    .clickable { onSelectHomeSet(item) }
                    .weight(1f)) {
                    Text(
                        text = item.title(),
                        style = MaterialTheme.typography.body1
                    )
                    Text(
                        text = item.url.encodedPath,
                        style = MaterialTheme.typography.caption.copy(fontFamily = FontFamily.Monospace)
                    )
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