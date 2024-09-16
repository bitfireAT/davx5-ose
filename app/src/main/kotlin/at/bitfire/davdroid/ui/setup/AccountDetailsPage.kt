/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.accounts.Account
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.composable.Assistant
import at.bitfire.vcard4android.GroupMethod

@Composable
fun AccountDetailsPage(
    snackbarHostState: SnackbarHostState,
    onAccountCreated: (Account) -> Unit,
    model: LoginScreenModel = viewModel()
) {
    val uiState by model.accountDetailsUiState.collectAsStateWithLifecycle()
    uiState.createdAccount?.let(onAccountCreated)

    val context = LocalContext.current
    LaunchedEffect(uiState.couldNotCreateAccount) {
        if (uiState.couldNotCreateAccount) {
            snackbarHostState.showSnackbar(context.getString(R.string.login_account_not_added))
            model.resetCouldNotCreateAccount()
        }
    }

    AccountDetailsPageContent(
        accountName = uiState.accountName,
        suggestedAccountNames = uiState.suggestedAccountNames,
        accountNameAlreadyExists = uiState.accountNameExists,
        onUpdateAccountName = { model.updateAccountName(it) },
        showApostropheWarning = uiState.showApostropheWarning,
        groupMethod = uiState.groupMethod,
        groupMethodReadOnly = uiState.groupMethodReadOnly,
        onUpdateGroupMethod = { model.updateGroupMethod(it) },
        onCreateAccount = { model.createAccount() },
        creatingAccount = uiState.creatingAccount
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailsPageContent(
    suggestedAccountNames: Set<String>,
    accountName: String,
    accountNameAlreadyExists: Boolean,
    onUpdateAccountName: (String) -> Unit = {},
    showApostropheWarning: Boolean,
    groupMethod: GroupMethod,
    groupMethodReadOnly: Boolean,
    onUpdateGroupMethod: (GroupMethod) -> Unit = {},
    onCreateAccount: () -> Unit = {},
    creatingAccount: Boolean
) {
    Assistant(
        nextLabel = stringResource(R.string.login_add_account),
        onNext = onCreateAccount,
        nextEnabled = !creatingAccount && accountName.isNotBlank() && !accountNameAlreadyExists,
        isLoading = creatingAccount
    ) {
        Column(Modifier.padding(8.dp)) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                val offerDropdown = suggestedAccountNames.isNotEmpty()
                OutlinedTextField(
                    value = accountName,
                    onValueChange = onUpdateAccountName,
                    label = { Text(stringResource(R.string.login_account_name)) },
                    isError = accountNameAlreadyExists,
                    supportingText =
                        if (accountNameAlreadyExists) {
                            { Text(stringResource(R.string.login_account_name_already_taken)) }
                        } else
                            null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email
                    ),
                    trailingIcon = if (offerDropdown) {
                        { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
                    } else null,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                if (offerDropdown)
                    DropdownMenu(   // ExposedDropdownMenu takes focus away from the text field when expanded
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        properties = PopupProperties(focusable = false)     // prevent focus from being taken away
                    ) {
                        for (name in suggestedAccountNames)
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    onUpdateAccountName(name)
                                    expanded = false
                                }
                            )
                    }
            }

            // apostrophe warning
            if (showApostropheWarning)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.padding(top = 8.dp, end = 8.dp, bottom = 8.dp)
                    )
                    Text(
                        stringResource(R.string.login_account_avoid_apostrophe),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

            // email address info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Icon(
                    Icons.Default.Email,
                    contentDescription = null,
                    modifier = Modifier.padding(top = 8.dp, end = 8.dp, bottom = 8.dp)
                )
                Text(
                    stringResource(R.string.login_account_name_info),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // group type selector
            Text(
                stringResource(R.string.login_account_contact_group_method),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp)
            )
            val groupMethodNames = stringArrayResource(R.array.settings_contact_group_method_entries)
            val groupMethodValues = stringArrayResource(R.array.settings_contact_group_method_values).map { GroupMethod.valueOf(it) }
            for ((name, method) in groupMethodNames.zip(groupMethodValues)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = groupMethod == method,
                        enabled = !groupMethodReadOnly,
                        onClick = { onUpdateGroupMethod(method) }
                    )

                    var modifier = Modifier.padding(vertical = 4.dp)
                    if (!groupMethodReadOnly)
                        modifier = modifier.clickable(onClick = { onUpdateGroupMethod(method) })
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = modifier
                    )
                }
            }
        }
    }
}

@Composable
@Preview
fun AccountDetailsPage_Content_Preview() {
    AccountDetailsPageContent(
        suggestedAccountNames = setOf("name1", "name2@example.com"),
        accountName = "account@example.com",
        accountNameAlreadyExists = false,
        showApostropheWarning = false,
        groupMethod = GroupMethod.GROUP_VCARDS,
        groupMethodReadOnly = false,
        creatingAccount = true
    )
}

@Composable
@Preview
fun AccountDetailsPage_Content_Preview_With_Apostrophe() {
    AccountDetailsPageContent(
        suggestedAccountNames = setOf("name1", "name2@example.com"),
        accountName = "account'example.com",
        accountNameAlreadyExists = true,
        showApostropheWarning = true,
        groupMethod = GroupMethod.CATEGORIES,
        groupMethodReadOnly = true,
        creatingAccount = false
    )
}