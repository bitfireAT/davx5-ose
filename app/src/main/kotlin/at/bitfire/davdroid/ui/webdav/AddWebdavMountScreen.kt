/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.webdav

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.Constants.withStatParams
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.composable.PasswordTextField

@Composable
fun AddWebDavMountScreen(
    onNavUp: () -> Unit = {},
    model: AddWebDavMountModel = viewModel()
) {
    val uiState by model.uiState
    AppTheme {
        AddWebDavMountForm(
            displayName = uiState.displayName,
            url = uiState.url,
            username = uiState.username,
            password = uiState.password,
            onNavUp = onNavUp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun AddWebDavMountForm(
    isLoading: Boolean = false,
    error: String? = null,
    onErrorClearRequested: () -> Unit = {},
    displayName: String = "",
    onDisplayNameChange: (String) -> Unit = {},
    displayNameError: String? = null,
    url: String = "",
    onUrlChange: (String) -> Unit = {},
    urlError: String? = null,
    username: String = "",
    onUsernameChange: (String) -> Unit = {},
    password: String = "",
    onPasswordChange: (String) -> Unit = {},
    onNavUp: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.showSnackbar(
                message = error
            )
            onErrorClearRequested()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavUp) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.navigate_up))
                    }
                },
                title = { Text(stringResource(R.string.webdav_add_mount_title)) },
                actions = {
                    TextButton(
                        enabled = !isLoading,
                        onClick = { /* ::validate */ }
                    ) {
                        Text(
                            text = stringResource(R.string.save)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            if (isLoading)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            Form(
                displayName,
                onDisplayNameChange,
                displayNameError,
                url,
                onUrlChange,
                urlError,
                username,
                onUsernameChange,
                password,
                onPasswordChange
            )
        }
    }
}

@Composable
fun Form(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    displayNameError: String?,
    url: String,
    onUrlChange: (String) -> Unit,
    urlError: String?,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        FormField(
            displayName,
            onDisplayNameChange,
            displayNameError,
            R.string.webdav_add_mount_display_name
        )
        FormField(
            url,
            onUrlChange,
            urlError,
            R.string.webdav_add_mount_url,
            trailingIcon = {
                val uriHandler = LocalUriHandler.current
                IconButton(
                    onClick = {
                        uriHandler.openUri(
                            Constants.HOMEPAGE_URL.buildUpon()
                                .appendPath(Constants.HOMEPAGE_PATH_TESTED_SERVICES)
                                .withStatParams("AddWebdavMountActivity")
                                .build().toString()
                        )
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Help, stringResource(R.string.help))
                }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.webdav_add_mount_authentication),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
        FormField(
            username,
            onUsernameChange,
            null,
            R.string.webdav_add_mount_username
        )

        PasswordTextField(
            password = password,
            onPasswordChange = onPasswordChange,
            labelText = stringResource(R.string.webdav_add_mount_password)
        )
    }
}

@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    @StringRes label: Int,
    trailingIcon: @Composable () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        label = { Text(stringResource(label)) },
        singleLine = true,
        isError = error != null,
        trailingIcon = trailingIcon
    )
    if (error != null) {
        Text(
            text = error,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium.copy(
                //color = MaterialTheme.colors.error
            )
        )
    }
}