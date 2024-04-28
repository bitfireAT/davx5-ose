/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.ui.AppTheme
import dagger.hilt.android.EntryPointAccessors
import okhttp3.HttpUrl.Companion.toHttpUrl

@Composable
fun CreateAddressBookScreen(
    account: Account,
    onNavUp: () -> Unit = {},
    onFinish: () -> Unit = {}
) {
    val context = LocalContext.current as Activity
    val entryPoint = EntryPointAccessors.fromActivity(context, CreateCollectionActivity.CreateCollectionEntryPoint::class.java)
    val model = viewModel<CreateCollectionModel>(
        factory = CreateCollectionModel.factoryFromAccount(entryPoint.createCollectionModelFactory(), account)
    )

    val uiState = model.uiState

    if (uiState.success)
        onFinish()

    CreateAddressBookScreen(
        displayName = uiState.displayName,
        onSetDisplayName = model::setDisplayName,
        description = uiState.description,
        onSetDescription = model::setDescription,
        homeSets = model.addressBookHomeSets.collectAsStateWithLifecycle(emptyList()).value,
        selectedHomeSet = uiState.selectedHomeSet,
        onSelectHomeSet = model::setHomeSet,
        canCreate = uiState.canCreate,
        isCreating = uiState.isCreating,
        onCreate = model::createAddressBook,
        onNavUp = onNavUp
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAddressBookScreen(
    displayName: String = "",
    onSetDisplayName: (String) -> Unit = {},
    description: String = "",
    onSetDescription: (String) -> Unit = {},
    homeSets: List<HomeSet>,
    selectedHomeSet: HomeSet? = null,
    onSelectHomeSet: (HomeSet) -> Unit = {},
    canCreate: Boolean = false,
    isCreating: Boolean = false,
    onCreate: () -> Unit = {},
    onNavUp: () -> Unit = {}
) {
    AppTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.create_addressbook)) },
                    navigationIcon = {
                        IconButton(onClick = onNavUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_up))
                        }
                    }
                )
            }
        ) { padding ->
            Column(Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
            ) {
                if (isCreating)
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    val focusRequester = remember { FocusRequester() }
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = onSetDisplayName,
                        label = { Text(stringResource(R.string.create_collection_display_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                    }

                    OutlinedTextField(
                        value = description,
                        onValueChange = onSetDescription,
                        label = { Text(stringResource(R.string.create_collection_description_optional)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                onCreate()
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )

                    HomeSetSelection(
                        homeSet = selectedHomeSet,
                        homeSets = homeSets,
                        onSelectHomeSet = onSelectHomeSet,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Button(
                        onClick = onCreate,
                        enabled = canCreate
                    ) {
                        Text(stringResource(R.string.create_addressbook))
                    }
                }
            }
        }
    }
}

@Composable
@Preview
fun CreateAddressBookScreen_Preview() {
    CreateAddressBookScreen(
        displayName = "Address Book",
        homeSets = listOf(
            HomeSet(0, 0, true, "https://example.com/some/homeset".toHttpUrl())
        )
    )
}