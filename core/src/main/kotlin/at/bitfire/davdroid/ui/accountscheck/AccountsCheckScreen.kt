/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.accountscheck

import android.accounts.Account
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
        onDeleteAndroidAccount = model::deleteAndroidAccount,
        onNavUp = onNavUp
    )
}

@Composable
fun AccountsCheckScreen(
    accounts: List<AccountStatus>?,
    onDeleteAndroidAccount: (Account) -> Unit = {},
    onNavUp: () -> Unit
) {
    AppTheme {
        var accountToDelete by rememberSaveable { mutableStateOf<Account?>(null) }
        accountToDelete?.let { account ->
            DeleteAndroidAccountDialog(
                accountName = account.name,
                onConfirm = {
                    onDeleteAndroidAccount(account)
                    accountToDelete = null
                },
                onDismiss = { accountToDelete = null }
            )
        }

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
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    Explanation()
                }

                item {
                    HorizontalDivider(Modifier.padding(vertical = 16.dp))
                }

                accountsStatus(accounts, onRequestDelete = { accountToDelete = it.account })
            }
        }
    }
}

@Composable
private fun Explanation() {
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
    AccountsCheckScreen(
        accounts = listOf(
            AccountStatus.MissingDbAccount(Account("orphan@example.com", "type")),
            AccountStatus.Linked("linked@example.com"),
            AccountStatus.Legacy("legacy@example.com")
        ),
        onNavUp = {}
    )
}

@Composable
@Preview
fun AccountsCheckScreen_Preview_Empty() {
    AccountsCheckScreen(
        accounts = emptyList(),
        onNavUp = {}
    )
}
