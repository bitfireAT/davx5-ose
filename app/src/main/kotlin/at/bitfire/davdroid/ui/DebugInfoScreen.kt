/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.accounts.Account
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.composable.CardWithImage
import at.bitfire.davdroid.ui.composable.ProgressBar
import java.io.File
import java.io.IOError
import java.io.IOException

@Composable
fun DebugInfoScreen(
    account: Account?,
    authority: String?,
    cause: Throwable?,
    localResource: String?,
    remoteResource: String?,
    logs: String?,
    timestamp: Long?,
    onShareZipFile: (File) -> Unit,
    onViewFile: (File) -> Unit,
    onNavUp: () -> Unit
) {
    val model: DebugInfoModel = hiltViewModel(
        creationCallback = { factory: DebugInfoModel.Factory ->
            factory.createWithDetails(DebugInfoModel.DebugInfoDetails(
                account = account,
                authority = authority,
                cause = cause,
                localResource = localResource,
                remoteResource = remoteResource,
                logs = logs,
                timestamp = timestamp
            ))
        }
    )

    val uiState = model.uiState
    val debugInfo = uiState.debugInfo
    val zipInProgress = uiState.zipInProgress
    val zipFile = uiState.zipFile
    val logFile = uiState.logFile
    val error = uiState.error

    // Share zip file card, once successfully generated
    LaunchedEffect(zipFile) {
        zipFile?.let { file ->
            onShareZipFile(file)
            model.resetZipFile()
        }
    }

    DebugInfoScreen(
        error = error,
        onResetError = model::resetError,
        showDebugInfo = debugInfo != null,
        zipProgress = zipInProgress,
        showModelCause = cause != null,
        modelCauseTitle = when (cause) {
            is HttpException -> stringResource(if (cause.code / 100 == 5) R.string.debug_info_server_error else R.string.debug_info_http_error)
            is DavException -> stringResource(R.string.debug_info_webdav_error)
            is IOException, is IOError -> stringResource(R.string.debug_info_io_error)
            else -> cause?.let { it::class.java.simpleName }
        } ?: "",
        modelCauseSubtitle = cause?.localizedMessage,
        modelCauseMessage = stringResource(
            if (cause is HttpException)
                when {
                    cause.code == 403 -> R.string.debug_info_http_403_description
                    cause.code == 404 -> R.string.debug_info_http_404_description
                    cause.code / 100 == 5 -> R.string.debug_info_http_5xx_description
                    else -> R.string.debug_info_unexpected_error
                }
            else
                R.string.debug_info_unexpected_error
        ),
        localResource = localResource,
        remoteResource = remoteResource,
        hasLogFile = logFile != null,
        onShareZip = { model.generateZip() },
        onViewLogsFile = { logFile?.let { onViewFile(it) } },
        onViewDebugFile = { debugInfo?.let { onViewFile(it) } },
        onNavUp = onNavUp
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugInfoScreen(
    error: String?,
    onResetError: () -> Unit = {},
    showDebugInfo: Boolean,
    zipProgress: Boolean,
    showModelCause: Boolean,
    modelCauseTitle: String,
    modelCauseSubtitle: String?,
    modelCauseMessage: String?,
    localResource: String?,
    remoteResource: String?,
    hasLogFile: Boolean,
    onShareZip: () -> Unit = {},
    onViewLogsFile: () -> Unit = {},
    onViewDebugFile: () -> Unit = {},
    onNavUp: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Long
            )
            onResetError()
        }
    }

    AppTheme {
        Scaffold(
            floatingActionButton = {
                if (showDebugInfo && !zipProgress) {
                    FloatingActionButton(
                        onClick = onShareZip,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Rounded.Share, stringResource(R.string.share))
                    }
                }
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.debug_info_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavUp) {
                            Icon(
                                Icons.AutoMirrored.Default.ArrowBack,
                                stringResource(R.string.navigate_up)
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                if (!showDebugInfo || zipProgress)
                    ProgressBar()

                CardWithImage(
                    title = stringResource(R.string.debug_info_credentials_warning_title),
                    message = stringResource(R.string.debug_info_credentials_warning_description),
                    icon = Icons.Rounded.PrivacyTip,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )

                if (showModelCause) {
                    CardWithImage(
                        title = modelCauseTitle,
                        subtitle = modelCauseSubtitle,
                        message = modelCauseMessage,
                        icon = Icons.Rounded.Info,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }

                if (showDebugInfo)
                    CardWithImage(
                        image = painterResource(R.drawable.undraw_server_down),
                        imageAlignment = BiasAlignment(0f, .7f),
                        title = stringResource(R.string.debug_info_title),
                        subtitle = stringResource(R.string.debug_info_subtitle),
                        icon = Icons.Rounded.BugReport,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onViewDebugFile,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Text(
                                stringResource(R.string.debug_info_view_details)
                            )
                        }
                    }

                if (localResource != null || remoteResource != null)
                    CardWithImage(
                        title = stringResource(R.string.debug_info_involved_caption),
                        subtitle = stringResource(R.string.debug_info_involved_subtitle),
                        icon = Icons.Rounded.Adb,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        remoteResource?.let {
                            Text(
                                text = stringResource(R.string.debug_info_involved_remote),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            SelectionContainer {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                        localResource?.let {
                            Text(
                                text = stringResource(R.string.debug_info_involved_local),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }

                if (hasLogFile) {
                    CardWithImage(
                        title = stringResource(R.string.debug_info_logs_caption),
                        subtitle = stringResource(R.string.debug_info_logs_subtitle),
                        icon = Icons.Rounded.BugReport,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onViewLogsFile,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Text(
                                stringResource(R.string.debug_info_logs_view)
                            )
                        }
                    }
                }

                if (showDebugInfo) {
                    CardWithImage(
                        title = stringResource(R.string.debug_info_archive_caption),
                        subtitle = stringResource(R.string.debug_info_archive_subtitle),
                        message = stringResource(R.string.debug_info_archive_text),
                        icon = Icons.Rounded.Share,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onShareZip,
                            enabled = !zipProgress,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Text(
                                stringResource(R.string.debug_info_archive_share)
                            )
                        }
                    }
                }

                // space for the FAB
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}

@Composable
@Preview
fun DebugInfoScreen_Preview() {
    DebugInfoScreen(
        error = "Some error",
        showDebugInfo = true,
        zipProgress = false,
        showModelCause = true,
        modelCauseTitle = "ModelCauseTitle",
        modelCauseSubtitle = "ModelCauseSubtitle",
        modelCauseMessage = "ModelCauseMessage",
        localResource = "local-resource-string",
        remoteResource = "remote-resource-string",
        hasLogFile = true
    )
}