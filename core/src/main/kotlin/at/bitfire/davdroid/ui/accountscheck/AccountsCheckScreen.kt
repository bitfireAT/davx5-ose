/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.accountscheck

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.ExternalUris
import at.bitfire.davdroid.ui.UiUtils.toAnnotatedString
import at.bitfire.davdroid.ui.composable.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsCheckScreen(
    onNavUp: () -> Unit
) {
    AppTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onNavUp) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.navigate_up))
                        }
                    },
                    title = { Text(stringResource(R.string.accounts_check_title)) }
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Explanation()
            }
        }
    }
}

@Composable
private fun Explanation() {
    Text(
        text = stringResource(R.string.accounts_check_android_accounts),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    val manualUrl = remember {
        ExternalUris.Manual.baseUrl.buildUpon()
            .appendPath(ExternalUris.Manual.PATH_ACCOUNTS_COLLECTIONS)
            .build()
            .toString()
    }
    Text(
        text = HtmlCompat.fromHtml(
            stringResource(R.string.accounts_check_explanation, stringResource(R.string.app_name), manualUrl),
            HtmlCompat.FROM_HTML_MODE_COMPACT
        ).toAnnotatedString(),
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
@Preview
fun AccountsCheckScreen_Preview() {
    AccountsCheckScreen(onNavUp = {})
}
