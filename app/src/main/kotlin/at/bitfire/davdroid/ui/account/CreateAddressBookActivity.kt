/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.TaskStackBuilder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.HomeSet
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.widget.ExceptionInfoDialog
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.apache.commons.lang3.StringUtils
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class CreateAddressBookActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    val account by lazy { intent.getParcelableExtra<Account>(EXTRA_ACCOUNT) ?: throw IllegalArgumentException("EXTRA_ACCOUNT must be set") }

    @Inject
    lateinit var modelFactory: AccountModel.Factory
    val model by viewModels<AccountModel> {
        object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                modelFactory.create(account) as T
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                var displayName by remember { mutableStateOf("") }
                var description by remember { mutableStateOf("") }
                var homeSet by remember { mutableStateOf<HomeSet?>(null) }

                var isCreating by remember { mutableStateOf(false) }
                model.createCollectionResult.observeAsState().value?.let { result ->
                    if (result.isEmpty)
                        finish()
                    else
                        ExceptionInfoDialog(
                            exception = result.get(),
                            onDismiss = {
                                isCreating = false
                                model.createCollectionResult.value = null
                            }
                        )
                }

                val onCreateCollection = {
                    if (!isCreating) {
                        isCreating = true
                        homeSet?.let { homeSet ->
                            model.createCollection(
                                homeSet = homeSet,
                                addressBook = true,
                                name = UUID.randomUUID().toString(),
                                displayName = StringUtils.trimToNull(displayName),
                                description = StringUtils.trimToNull(description)
                            )
                        }
                    }
                }

                val homeSets by model.bindableAddressBookHomesets.observeAsState()

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.create_addressbook)) },
                            navigationIcon = {
                                IconButton(onClick = { onSupportNavigateUp() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.navigate_up))
                                }
                            },
                            actions = {
                                val isCreateEnabled = !isCreating && displayName.isNotEmpty() && homeSet != null
                                IconButton(
                                    enabled = isCreateEnabled,
                                    onClick = { onCreateCollection() }
                                ) {
                                    Text(stringResource(R.string.create_collection_create).uppercase())
                                }
                            }
                        )
                    }
                ) { padding ->
                    Column(
                        Modifier
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (isCreating)
                            LinearProgressIndicator(
                                color = MaterialTheme.colors.secondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            )

                        homeSets?.let { homeSets ->
                            AddressBookForm(
                                displayName = displayName,
                                onDisplayNameChange = { displayName = it },
                                description = description,
                                onDescriptionChange = { description = it },
                                homeSets = homeSets,
                                homeSet = homeSet,
                                onHomeSetSelected = { homeSet = it },
                                onCreateCollection = {
                                    onCreateCollection()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun supportShouldUpRecreateTask(targetIntent: Intent) = true

    override fun onPrepareSupportNavigateUpTaskStack(builder: TaskStackBuilder) {
        builder.editIntentAt(builder.intentCount - 1)?.putExtra(AccountActivity.EXTRA_ACCOUNT, model.account)
    }


    @Composable
    private fun AddressBookForm(
        displayName: String,
        onDisplayNameChange: (String) -> Unit = {},
        description: String,
        onDescriptionChange: (String) -> Unit = {},
        homeSet: HomeSet?,
        homeSets: List<HomeSet>,
        onHomeSetSelected: (HomeSet) -> Unit = {},
        onCreateCollection: () -> Unit = {}
    ) {
        Column(Modifier
            .fillMaxWidth()
            .padding(8.dp)
        ) {
            val focusRequester = remember { FocusRequester() }
            OutlinedTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
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
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.create_collection_description_optional)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        onCreateCollection()
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            HomeSetSelection(
                homeSet = homeSet,
                homeSets = homeSets,
                onHomeSetSelected = onHomeSetSelected
            )
        }
    }

    @Composable
    @Preview
    private fun AddressBookForm_Preview() {
        AddressBookForm(
            displayName = "Display Name",
            description = "Some longer description that is optional",
            homeSets = listOf(
                HomeSet(1, 0, false, "http://example.com/".toHttpUrl()),
                HomeSet(2, 0, false, "http://example.com/".toHttpUrl(), displayName = "Home Set 2")
            ),
            homeSet = null
        )
    }

}