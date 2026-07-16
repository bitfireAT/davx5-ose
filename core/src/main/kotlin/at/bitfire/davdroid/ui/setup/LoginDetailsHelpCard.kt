/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.ExternalUris
import at.bitfire.davdroid.ui.ExternalUris.withStatParams
import at.bitfire.davdroid.ui.UiUtils.toAnnotatedString
import at.bitfire.davdroid.ui.composable.IconCard

@Composable
@Preview
fun LoginDetailsHelpCard(
    includeServiceDiscovery: Boolean = false,
    screenName: String? = null
) {
    IconCard(
        icon = Icons.Default.Info,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        val context = LocalContext.current
        Column {
            if (includeServiceDiscovery) {
                val manualUrl = ExternalUris.Manual.baseUrl.buildUpon()
                    .appendPath(ExternalUris.Manual.PATH_ACCOUNTS_COLLECTIONS)
                    .fragment(ExternalUris.Manual.FRAGMENT_SERVICE_DISCOVERY)
                    .withStatParams(context, screen = screenName)
                    .build()
                val urlInfo = HtmlCompat.fromHtml(
                    stringResource(R.string.login_base_url_info, manualUrl),
                    HtmlCompat.FROM_HTML_MODE_COMPACT
                )
                Text(
                    text = urlInfo.toAnnotatedString(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            val testedWith = ExternalUris.Homepage.baseUrl.buildUpon()
                .appendPath(ExternalUris.Homepage.PATH_TESTED_SERVICES)
                .withStatParams(context, screen = "UrlLoginScreen")
                .build()
            Text(
                AnnotatedString.fromHtml(stringResource(R.string.login_see_tested_with, testedWith.toString())),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
@Preview
fun LoginDetailsHelpCard_Preview_IncludingServiceDiscovery() {
    LoginDetailsHelpCard(includeServiceDiscovery = true)
}