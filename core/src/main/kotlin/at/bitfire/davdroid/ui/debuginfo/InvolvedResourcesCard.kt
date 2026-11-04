/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.debuginfo

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.composable.CardWithImage

@Composable
internal fun InvolvedResourcesCard(
    localResource: String?,
    canViewResource: Boolean,
    onViewLocalResource: () -> Unit,
    remoteResource: String?,
    onCopyRemoteUrl: () -> Unit,
    canViewRemoteResource: Boolean = false,
    remoteResourceInProgress: Boolean = false,
    onViewRemoteResource: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    CardWithImage(
        title = stringResource(R.string.debug_info_involved_caption),
        subtitle = stringResource(R.string.debug_info_involved_subtitle),
        icon = Icons.Rounded.Adb,
        modifier = modifier
    ) {
        if (localResource != null) {
            LocalResource(localResource, canViewResource, onViewLocalResource)
        }

        if (remoteResource != null) {
            RemoteResource(
                remoteResource,
                onCopyRemoteUrl,
                canViewRemoteResource,
                remoteResourceInProgress,
                onViewRemoteResource
            )
        }
    }
}

@Composable
private fun LocalResource(
    localResource: String,
    canViewResource: Boolean,
    onViewLocalResource: () -> Unit
) {
    Text(
        text = stringResource(R.string.debug_info_involved_local),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 4.dp)
    )

    Text(
        text = localResource,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace
        ),
        modifier = Modifier.padding(bottom = 8.dp)
    )

    if (canViewResource) {
        OutlinedButton(
            onClick = { onViewLocalResource() },
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Text(stringResource(R.string.debug_info_view_local_resource))
        }
    }
}

@Composable
private fun RemoteResource(
    remoteResource: String,
    onCopyRemoteUrl: () -> Unit,
    canViewRemoteResource: Boolean,
    remoteResourceInProgress: Boolean,
    onViewRemoteResource: () -> Unit
) {
    Text(
        text = stringResource(R.string.debug_info_involved_remote),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 4.dp)
    )

    SelectionContainer {
        Text(
            text = remoteResource,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }

    Row(modifier = Modifier.padding(bottom = 4.dp)) {
        OutlinedButton(
            onClick = onCopyRemoteUrl,
            modifier = Modifier.padding(end = 8.dp)
        ) {
            Text(stringResource(R.string.debug_info_copy_remote_url))
        }

        if (canViewRemoteResource) {
            OutlinedButton(
                onClick = onViewRemoteResource,
                enabled = !remoteResourceInProgress
            ) {
                Text(stringResource(R.string.debug_info_view_remote_resource))
            }
        }
    }
}

@Composable
@Preview
fun InvolvedResourcesCard_Preview() {
    Surface {
        InvolvedResourcesCard(
            localResource = "content://example.authority/resource",
            canViewResource = true,
            onViewLocalResource = {},
            remoteResource = "https://domain.example/resource",
            onCopyRemoteUrl = {},
            canViewRemoteResource = true,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
@Preview
fun InvolvedResourcesCard_CanNotViewLocalResource_Preview() {
    Surface {
        InvolvedResourcesCard(
            localResource = "content://example.authority/resource",
            canViewResource = false,
            onViewLocalResource = {},
            remoteResource = "https://domain.example/resource",
            onCopyRemoteUrl = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
@Preview
fun InvolvedResourcesCard_OnlyLocalResource_Preview() {
    Surface {
        InvolvedResourcesCard(
            localResource = "content://example.authority/resource",
            canViewResource = true,
            onViewLocalResource = {},
            remoteResource = null,
            onCopyRemoteUrl = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
@Preview
fun InvolvedResourcesCard_OnlyRemoteResource_Preview() {
    Surface {
        InvolvedResourcesCard(
            localResource = null,
            canViewResource = false,
            onViewLocalResource = {},
            remoteResource = "https://domain.example/resource",
            onCopyRemoteUrl = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}
