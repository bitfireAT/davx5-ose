package at.bitfire.davdroid.ui.setup

import android.accounts.Account
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.R
import at.bitfire.davdroid.servicedetection.DavResourceFinder
import at.bitfire.davdroid.ui.composable.Assistant
import at.bitfire.davdroid.ui.widget.ExceptionInfoDialog
import at.bitfire.vcard4android.GroupMethod

@Composable
fun AccountDetailsPage(
    foundConfig: DavResourceFinder.Configuration,
    onBack: () -> Unit,
    onAccountCreated: (Account) -> Unit,
    model: LoginModel = viewModel()
) {
    var accountName by remember { mutableStateOf(foundConfig.calDAV?.emails?.firstOrNull() ?: "") }
    val forcedGroupMethod by model.forcedGroupMethod.observeAsState()

    BackHandler(onBack = onBack)

    val resultOrNull by model.createAccountResult.observeAsState()
    var showExceptionInfo by remember { mutableStateOf(false) }
    LaunchedEffect(resultOrNull) {
        showExceptionInfo = resultOrNull != null
    }
    if (showExceptionInfo)
        resultOrNull?.let { result ->
            when (result) {
                is LoginModel.CreateAccountResult.Success -> {
                    onAccountCreated(result.account)
                }
                is LoginModel.CreateAccountResult.Error -> {
                    if (result.exception != null)
                        ExceptionInfoDialog(
                            result.exception,
                            onDismiss = {
                                model.createAccountResult.value = null
                            }
                        )
                    // TODO else
                }
            }
        }

    AccountDetailsPage_Content(
        suggestedAccountNames = foundConfig.calDAV?.emails,
        accountName = accountName,
        onUpdateAccountName = { accountName = it },
        onCreateAccount = {
            model.createAccount(
                foundConfig = foundConfig,
                name = accountName
            )
        },
        showGroupMethod = forcedGroupMethod == null
    )
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AccountDetailsPage_Content(
    suggestedAccountNames: List<String>?,
    accountName: String,
    onUpdateAccountName: (String) -> Unit = {},
    showGroupMethod: Boolean = true,
    onCreateAccount: () -> Unit = {}
) {
    Assistant(
        nextLabel = stringResource(R.string.login_create_account),
        onNext = onCreateAccount
    ) {
        Column(Modifier.padding(8.dp)) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = accountName,
                    onValueChange = onUpdateAccountName,
                    label = { Text(stringResource(R.string.login_account_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email
                    ),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(
                            expanded = expanded
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    if (suggestedAccountNames != null)
                        for (name in suggestedAccountNames)
                            Text(
                                name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .clickable {
                                        onUpdateAccountName(name)
                                        expanded = false
                                    }
                            )
                }
            }

            // apostrophe warning
            if (accountName.contains('\'') || accountName.contains('"'))
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
                        style = MaterialTheme.typography.body1
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
                    style = MaterialTheme.typography.body1
                )
            }

            // group type selector
            if (showGroupMethod) {
                Text(
                    stringResource(R.string.login_account_contact_group_method),
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier.padding(top = 16.dp)
                )
                var groupMethod by remember { mutableStateOf(GroupMethod.GROUP_VCARDS) }
                val groupMethodNames = stringArrayResource(R.array.settings_contact_group_method_entries)
                val groupMethodValues = stringArrayResource(R.array.settings_contact_group_method_values).map { GroupMethod.valueOf(it) }
                for ((name, method) in groupMethodNames.zip(groupMethodValues)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = groupMethod == method,
                            onClick = { groupMethod = method }
                        )
                        Text(
                            name,
                            style = MaterialTheme.typography.body1,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .clickable(onClick = { groupMethod = method })
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Preview
fun AccountDetailsPage_Content_Preview() {
    AccountDetailsPage_Content(
        suggestedAccountNames = listOf("name1", "name2@example.com"),
        accountName = "account@example.com"
    )
}

@Composable
@Preview
fun AccountDetailsPage_Content_Preview_With_Apostrophe() {
    AccountDetailsPage_Content(
        suggestedAccountNames = listOf("name1", "name2@example.com"),
        accountName = "account'example.com"
    )
}