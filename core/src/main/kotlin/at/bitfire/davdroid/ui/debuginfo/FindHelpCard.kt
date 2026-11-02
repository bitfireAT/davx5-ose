/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.debuginfo

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LiveHelp
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.composable.CardWithImage
import at.bitfire.davdroid.ui.icon.Github

@Composable
internal fun FindHelpCard(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current

    CardWithImage(
        title = stringResource(R.string.debug_info_find_help),
        icon = Icons.AutoMirrored.Rounded.LiveHelp,
        modifier = modifier
    ) {
        Text(
            text = AnnotatedString.fromHtml(stringResource(R.string.debug_info_find_help_faq))
        )
        OutlinedButton(
            onClick = { uriHandler.openUri("https://www.davx5.com/faq") },
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(stringResource(R.string.debug_info_find_help_faq_action))
        }

        Text(
            text = AnnotatedString.fromHtml(stringResource(R.string.debug_info_find_help_discussions))
        )
        OutlinedButton(
            onClick = { uriHandler.openUri("https://github.com/bitfireAT/davx5-ose/discussions") },
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(Github, stringResource(R.string.debug_info_find_help_discussions_action))
            Text(stringResource(R.string.debug_info_find_help_discussions_action), Modifier.padding(start = 4.dp))
        }

        Text(
            text = AnnotatedString.fromHtml(stringResource(R.string.debug_info_find_help_issues))
        )
        OutlinedButton(
            onClick = { uriHandler.openUri("https://github.com/bitfireAT/davx5-ose/issues") },
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(Github, stringResource(R.string.debug_info_find_help_issues_action))
            Text(stringResource(R.string.debug_info_find_help_issues_action), Modifier.padding(start = 4.dp))
        }

        Text(
            text = AnnotatedString.fromHtml(stringResource(R.string.debug_info_find_help_manual))
        )
        OutlinedButton(
            onClick = { uriHandler.openUri("https://manual.davx5.com/") },
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Text(stringResource(R.string.debug_info_find_help_manual_action))
        }
    }
}

@Composable
@Preview
fun FindHelpCard_Preview() {
    Surface {
        FindHelpCard(modifier = Modifier.padding(16.dp))
    }
}
