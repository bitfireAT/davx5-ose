/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.preconfiguredsettings.externalscanner

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertLink
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import at.bitfire.davdroid.ui.ExternalUris
import at.bitfire.davdroid.ui.composable.Assistant
import at.bitfire.davdroid.ui.setup.LoginInfo
import at.bitfire.davdroid.ui.setup.LoginType
import com.davx5.feature.preconfiguredsettings.shared.LocalizedException
import io.ktor.http.parseUrl
import at.bitfire.davdroid.R as oseR
import com.davx5.feature.preconfiguredsettings.shared.R as sharedR

object PreconfiguredSettingsLogin : LoginType {

    override val title: Int
        get() = sharedR.string.preconfigured_settings_login_type_preconfigured

    override val helpUrl: Uri
        get() = ExternalUris.Homepage.baseUrl.buildUpon()
            .appendPath(ExternalUris.Homepage.PATH_DAVX5_SELECT)
            .build()

    @Composable
    override fun LoginScreen(
        snackbarHostState: SnackbarHostState,
        initialLoginInfo: LoginInfo,
        onLogin: (LoginInfo) -> Unit
    ) {
        LoginScreen(snackbarHostState, onLogin)
    }

    @Composable
    private fun LoginScreen(
        snackbarHostState: SnackbarHostState,
        onLogin: (LoginInfo) -> Unit,
        viewModel: PreconfiguredSettingsViewModel = hiltViewModel()
    ) {
        val scanQrCodeLauncher = rememberLauncherForActivityResult(ScanQrCode()) { result ->
            if (result != null) {
                viewModel.handleScanResult(result, onLogin)
            }
        }

        val error by viewModel.error.collectAsState()
        var url by remember { mutableStateOf("") }

        // Handle URL from intent
        val activity = LocalActivity.current
        LaunchedEffect(Unit) {
            val intentUri = activity?.intent?.data
            if (intentUri != null) {
                val configUrl = viewModel.extractConfigUrl(intentUri)

                // start fetching automatically, but only once (not again after navigating back or on recomposition)
                if (configUrl != null && viewModel.markIntentHandled()) {
                    viewModel.fetchPreconfiguredSettings(configUrl, onLogin)
                }
            }

            if (intentUri?.scheme == "davx5") {
                val configUrl = intentUri.getQueryParameter("config")
                if (!configUrl.isNullOrEmpty()) {
                    url = configUrl
                }
            }
        }

        val context = LocalContext.current
        LaunchedEffect(error) {
            val error = error
            if (error != null) {
                val message = if (error is LocalizedException) {
                    error.localizedMessage(context)
                } else {
                    error.localizedMessage ?: error.message ?: error.toString()
                }

                snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
                viewModel.dismissError()
            }
        }

        val isUrlValid = parseUrl(url) != null
        Assistant(
            nextLabel = stringResource(oseR.string.login_login),
            nextEnabled = isUrlValid,
            onNext = {
                viewModel.fetchPreconfiguredSettings(url, onLogin)
            }
        ) {
            Column(Modifier.padding(12.dp)) {
                val urlError = url.isNotBlank() && !isUrlValid

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.InsertLink,
                            contentDescription = stringResource(sharedR.string.preconfigured_settings_profile_url)
                        )
                    },
                    supportingText = {
                        val supportingText = if (urlError) {
                            stringResource(oseR.string.webdav_add_mount_url_invalid)
                        } else {
                            stringResource(sharedR.string.preconfigured_settings_profile_url_help)
                        }
                        Text(supportingText)
                    },
                    label = {
                        Text(stringResource(sharedR.string.preconfigured_settings_profile_url))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    isError = urlError
                )

                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        try {
                            scanQrCodeLauncher.launch(Unit)
                        } catch (_: ActivityNotFoundException) {
                            viewModel.onScanQrCodeFailure()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = stringResource(sharedR.string.preconfigured_settings_scan_qr)
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(stringResource(sharedR.string.preconfigured_settings_scan_qr))
                }
            }
        }
    }
}
