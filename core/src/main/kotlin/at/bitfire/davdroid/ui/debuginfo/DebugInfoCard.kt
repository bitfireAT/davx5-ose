/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.debuginfo

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.composable.CardWithImage

@Composable
internal fun DebugInfoCard(
    onViewDebugFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    CardWithImage(
        title = stringResource(R.string.debug_info_title),
        subtitle = stringResource(R.string.debug_info_subtitle),
        icon = Icons.Rounded.BugReport,
        modifier = modifier
    ) {
        OutlinedButton(
            onClick = onViewDebugFile,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Text(
                stringResource(R.string.debug_info_view_details)
            )
        }
    }
}

@Composable
@Preview
fun DebugInfoCard_Preview() {
    Surface {
        DebugInfoCard(
            onViewDebugFile = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}
