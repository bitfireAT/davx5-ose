/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.debuginfo

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
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
internal fun DebugLogZipCard(
    onShareZip: () -> Unit,
    zipProgress: Boolean,
    modifier: Modifier = Modifier
) {
    CardWithImage(
        title = stringResource(R.string.debug_info_archive_caption),
        subtitle = stringResource(R.string.debug_info_archive_subtitle),
        message = stringResource(R.string.debug_info_archive_text),
        icon = Icons.Rounded.Share,
        modifier = modifier
    ) {
        OutlinedButton(
            onClick = onShareZip,
            enabled = !zipProgress,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Text(
                stringResource(R.string.debug_info_archive_share)
            )
        }
    }
}

@Composable
@Preview
fun DebugLogZipCard_Preview() {
    Surface {
        DebugLogZipCard(
            onShareZip = {},
            zipProgress = false,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
@Preview
fun DebugLogZipCard_InProgress_Preview() {
    Surface {
        DebugLogZipCard(
            onShareZip = {},
            zipProgress = true,
            modifier = Modifier.padding(16.dp)
        )
    }
}
