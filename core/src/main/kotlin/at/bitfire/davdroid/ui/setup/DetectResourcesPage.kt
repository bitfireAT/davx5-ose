/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.DebugInfoActivity
import at.bitfire.davdroid.ui.ExternalUris
import at.bitfire.davdroid.ui.ExternalUris.withStatParams
import at.bitfire.davdroid.ui.UiUtils.toAnnotatedString
import at.bitfire.davdroid.ui.composable.ProgressBar

@Composable
fun DetectResourcesPage(
    model: LoginScreenModel = viewModel()
) {
    val permissionRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) model.retryResourceDetection()
    }

    val uiState = model.detectResourcesUiState
    DetectResourcesPageContent(
        loading = uiState.loading,
        foundNothing = uiState.foundNothing,
        encountered401 = uiState.encountered401,
        loginValidationFailed = uiState.loginValidationFailed,
        logs = uiState.logs,
        missingLocalPermission = uiState.missingLocalPermission,
        onRequestLocalPermissionRequest = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
                permissionRequestLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            }
        }
    )
}

@Composable
fun DetectResourcesPageContent(
    loading: Boolean,
    foundNothing: Boolean,
    encountered401: Boolean,
    loginValidationFailed: Boolean,
    missingLocalPermission: Boolean,
    onRequestLocalPermissionRequest: () -> Unit,
    logs: String?
) {
    Column(Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
    ) {
        if (loading)
            DetectResourcesPageContent_InProgress()
        else if (loginValidationFailed)
            DetectResourcesPageContent_LoginValidationFailed()
        else if(missingLocalPermission)
            DetectResourcesPageContent_MissingPermission(onRequestLocalPermissionRequest)
        else if (foundNothing)
            DetectResourcesPageContent_NothingFound(
                encountered401 = encountered401,
                logs = logs
            )
    }
}

@Composable
@Preview
fun DetectResourcesPageContent_InProgress() {
    Column(Modifier.fillMaxWidth()) {
        ProgressBar(
            //color = MaterialTheme.colors.secondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp))

        Column(Modifier.padding(8.dp)) {
            Text(
                stringResource(R.string.login_configuration_detection),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                stringResource(R.string.login_querying_server),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun DetectResourcesPageContent_NothingFound(
    encountered401: Boolean,
    logs: String?
) {
    Column(Modifier.padding(8.dp)) {
        Text(
            stringResource(R.string.login_configuration_detection),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        stringResource(R.string.login_no_service),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    stringResource(R.string.login_no_service_info),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )

                val context = LocalContext.current
                val urlServices = ExternalUris.Homepage.baseUrl.buildUpon()
                    .appendPath(ExternalUris.Homepage.PATH_TESTED_SERVICES)
                    .withStatParams(context, "DetectResourcesPage")
                    .build()
                val testedServices = HtmlCompat.fromHtml(
                    stringResource(R.string.login_see_tested_services, urlServices),
                    HtmlCompat.FROM_HTML_MODE_COMPACT
                ).toAnnotatedString()
                Text(
                    text = testedServices,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                if (encountered401)
                    Text(
                        stringResource(R.string.login_check_credentials),
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )

                if (logs != null && logs.isNotEmpty()) {
                    Text(
                        stringResource(R.string.login_logs_available),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Button(
                        onClick = {
                            val intent = DebugInfoActivity.IntentBuilder(context)
                                .withLogs(logs)
                                .build()
                            context.startActivity(intent)
                        }
                    ) {
                        Text(stringResource(R.string.login_view_logs))
                    }
                }
            }
        }
    }
}

@Composable
fun DetectResourcesPageContent_LoginValidationFailed() {
    Column(Modifier.padding(8.dp)) {
        Text(
            stringResource(R.string.login_configuration_detection),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        stringResource(R.string.login_invalid_use_case),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    stringResource(R.string.login_invalid_use_case_info, "(${stringResource(R.string.app_name)})"),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )

                val context = LocalContext.current
                val urlDownload = ExternalUris.Homepage.baseUrl.buildUpon()
                    .appendPath(ExternalUris.Homepage.PATH_DOWNLOAD)
                    .withStatParams(context, "LoginValidationFailedPage")
                    .build()
                Text(
                    text = HtmlCompat.fromHtml(
                        stringResource(R.string.login_download_regular_davx5, urlDownload),
                        HtmlCompat.FROM_HTML_MODE_COMPACT
                    ).toAnnotatedString(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Button(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, urlDownload.toString().toUri()))
                }) {
                    Text(stringResource(R.string.login_download_button))
                }
            }
        }
    }
}

@Composable
fun DetectResourcesPageContent_MissingPermission(onRequestPermissionRequest: () -> Unit) {
    Column(Modifier.padding(8.dp)) {
        Text(
            stringResource(R.string.login_configuration_detection),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        stringResource(R.string.login_permission_required),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    stringResource(R.string.login_local_permission),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )

                Button(
                    onClick = onRequestPermissionRequest
                ) {
                    Text(stringResource(R.string.login_permission_grant))
                }
            }
        }
    }
}

@Composable
@Preview
fun DetectResourcesPageContent_NothingFound() {
    DetectResourcesPageContent_NothingFound(
        encountered401 = false,
        logs = "SOME LOGS"
    )
}

@Composable
@Preview
fun DetectResourcesPage_NothingFound_401() {
    DetectResourcesPageContent_NothingFound(
        encountered401 = true,
        logs = ""
    )
}

@Composable
@Preview
fun DetectResourcesPage_ValidationFailed() {
    DetectResourcesPageContent_LoginValidationFailed()
}

@Composable
@Preview
fun DetectResourcesPageContent_MissingPermission() {
    DetectResourcesPageContent_MissingPermission(onRequestPermissionRequest = {})
}