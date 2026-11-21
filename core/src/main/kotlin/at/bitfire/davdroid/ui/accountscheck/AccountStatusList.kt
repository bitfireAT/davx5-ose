/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.accountscheck

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.composable.ProgressBar

internal fun LazyListScope.accountsStatus(
    accounts: List<AccountStatus>?,
    onRequestDelete: (AccountStatus.MissingDbAccount) -> Unit
) {
    item {
        Text(
            text = stringResource(R.string.accounts_check_status),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }

    when {
        accounts == null -> item {
            ProgressBar(Modifier.fillMaxWidth())
        }
        accounts.isEmpty() -> item {
            Text(
                text = stringResource(R.string.accounts_check_empty),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp)
            )
        }
        else ->
            // no keys: a legacy and a DB account can have the same name
            items(accounts) { account ->
                AccountStatusItem(account, onRequestDelete)
            }
    }
}

@Composable
private fun AccountStatusItem(
    status: AccountStatus,
    onRequestDelete: (AccountStatus.MissingDbAccount) -> Unit
) {
    val (description, descriptionColor) = when (status) {
        is AccountStatus.Legacy ->
            stringResource(R.string.accounts_check_legacy_account) to MaterialTheme.colorScheme.secondary
        is AccountStatus.Linked ->
            stringResource(R.string.accounts_check_links_to_db_account) to MaterialTheme.colorScheme.primary
        is AccountStatus.MissingDbAccount ->
            stringResource(R.string.accounts_check_missing_db_account) to MaterialTheme.colorScheme.error
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (status.isProblem) Icons.Default.Warning else Icons.Default.Check,
                contentDescription = null,
                tint = if (status.isProblem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = ICON_TEXT_GAP)
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

        if (status is AccountStatus.MissingDbAccount)
            Button(
                onClick = { onRequestDelete(status) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.padding(start = ICON_SIZE + ICON_TEXT_GAP, top = 8.dp)
            ) {
                Text(stringResource(R.string.dialog_delete))
            }
    }
}

private val ICON_SIZE = 24.dp
private val ICON_TEXT_GAP = 16.dp
