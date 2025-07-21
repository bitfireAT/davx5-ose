/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.webdav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.AppTheme
import at.bitfire.davdroid.ui.composable.PasswordTextField
import at.bitfire.davdroid.ui.composable.ProgressBar
import at.bitfire.davdroid.ui.composable.SelectClientCertificateCard

@Composable
fun AddWebdavMountScreen(
    onNavUp: () -> Unit = {},
    onFinish: () -> Unit = {},
    model: AddWebdavMountModel = viewModel()
) {
    val uiState = model.uiState

    if (uiState.success) {
        onFinish()
        return
    }

    AppTheme {
        AddWebDavMountScreen(
            isLoading = uiState.isLoading,
            error = uiState.error,
            onResetError = model::resetError,
            displayName = uiState.displayName,
            onSetDisplayName = model::setDisplayName,
            url = uiState.url,
            onSetUrl = model::setUrl,
            username = uiState.username,
            onSetUsername = model::setUsername,
            password = uiState.password,
            onSetPassword = model::setPassword,
            certificateAlias = uiState.certificateAlias,
            onSetCertificateAlias = model::setCertificateAlias,
            canContinue = uiState.canContinue,
            onAddMount = { model.addMount() },
            onNavUp = onNavUp
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AddWebDavMountScreen(
    isLoading: Boolean,
    error: String?,
    onResetError: () -> Unit = {},
    displayName: String,
    onSetDisplayName: (String) -> Unit = {},
    url: String,
    onSetUrl: (String) -> Unit = {},
    username: String,
    onSetUsername: (String) -> Unit = {},
    password: String,
    onSetPassword: (String) -> Unit = {},
    certificateAlias: String?,
    onSetCertificateAlias: (String) -> Unit = {},
    canContinue: Boolean,
    onAddMount: () -> Unit = {},
    onNavUp: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.showSnackbar(error)
            onResetError()
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
                    val uriHandler = LocalUriHandler.current
                    IconButton(
                        onClick = {
                            uriHandler.openUri(webdavMountsHelpUrl().toString())
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Help,
                            contentDescription = stringResource(R.string.help)
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
                ProgressBar(modifier = Modifier.fillMaxWidth())

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                val focusRequester = remember { FocusRequester() }

                Text(
                    text = stringResource(R.string.webdav_add_mount_mountpoint_displayname),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    label = { Text(stringResource(R.string.webdav_add_mount_url)) },
                    leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                    placeholder = { Text("dav.example.com") },
                    value = url,
                    onValueChange = onSetUrl,
                    singleLine = true,
                    readOnly = isLoading,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next,
                        keyboardType = KeyboardType.Uri
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .focusRequester(focusRequester)
                )
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                }

                OutlinedTextField(
                    label = { Text(stringResource(R.string.webdav_add_mount_display_name)) },
                    value = displayName,
                    onValueChange = onSetDisplayName,
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Sell, null)
                    },
                    readOnly = isLoading,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                Text(
                    text = stringResource(R.string.webdav_add_mount_authentication),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    label = { Text(stringResource(R.string.login_user_name_optional)) },
                    value = username,
                    onValueChange = onSetUsername,
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.AccountCircle, null)
                    },
                    readOnly = isLoading,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next,
                        keyboardType = KeyboardType.Email
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                PasswordTextField(
                    password = password,
                    onPasswordChange = onSetPassword,
                    labelText = stringResource(R.string.login_password_optional),
                    readOnly = isLoading,
                    leadingIcon = {
                        Icon(Icons.Default.Password, null)
                    },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onAddMount() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
                SelectClientCertificateCard(
                    snackbarHostState = snackbarHostState,
                    enabled = !isLoading,
                    chosenAlias = certificateAlias,
                    onAliasChosen = onSetCertificateAlias,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                Button(
                    enabled = canContinue && !isLoading,
                    onClick = { onAddMount() }
                ) {
                    Text(
                        text = stringResource(R.string.webdav_add_mount_add)
                    )
                }
            }
        }
    }
}

@Composable
@Preview
fun AddWebDavMountScreen_Preview() {
    AppTheme {
        AddWebDavMountScreen(
            isLoading = true,
            error = null,
            displayName = "Test",
            url = "https://example.com",
            username = "user",
            password = "password",
            certificateAlias = null,
            canContinue = true
        )
    }
}