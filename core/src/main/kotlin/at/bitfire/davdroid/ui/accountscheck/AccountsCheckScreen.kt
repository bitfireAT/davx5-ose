/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.accountscheck

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.ExternalUris
import at.bitfire.davdroid.ui.UiUtils.toAnnotatedString
import at.bitfire.davdroid.ui.composable.AppTheme

@Composable
fun AccountsCheckScreen(
    onNavUp: () -> Unit,
    model: AccountsCheckViewModel = hiltViewModel()
) {
    val accounts by model.accounts.collectAsStateWithLifecycle()
    AccountsCheckScreen(
        accounts = accounts,
        onNavUp = onNavUp
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsCheckScreen(
    accounts: List<AccountStatus>?,
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

                HorizontalDivider(Modifier.padding(vertical = 16.dp))

                AccountsStatus(accounts)
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
private fun AccountsStatus(accounts: List<AccountStatus>?) {
    Text(
        text = stringResource(R.string.accounts_check_status),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    if (accounts != null)
        for (account in accounts)
            AccountStatusItem(account)
}

@Composable
private fun AccountStatusItem(status: AccountStatus) {
    val (description, descriptionColor) = when (status) {
        is AccountStatus.Legacy ->
            stringResource(R.string.accounts_check_legacy_account) to MaterialTheme.colorScheme.secondary
        is AccountStatus.Linked ->
            stringResource(R.string.accounts_check_links_to_db_account) to MaterialTheme.colorScheme.primary
        is AccountStatus.MissingDbAccount ->
            stringResource(R.string.accounts_check_missing_db_account) to MaterialTheme.colorScheme.error
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = if (status.isProblem) Icons.Default.Warning else Icons.Default.Check,
            contentDescription = null,
            tint = if (status.isProblem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 16.dp)
        )

        Column {
            Text(
                text = status.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = descriptionColor
            )
        }
    }
}

@Composable
@Preview
fun AccountsCheckScreen_Preview() {
    AccountsCheckScreen(
        accounts = listOf(
            AccountStatus.MissingDbAccount("orphan@example.com"),
            AccountStatus.Linked("linked@example.com"),
            AccountStatus.Legacy("legacy@example.com")
        ),
        onNavUp = {}
    )
}
