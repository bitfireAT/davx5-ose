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
import androidx.compose.material.icons.automirrored.rounded.LiveHelp
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import at.bitfire.dav4jvm.ktor.exception.DavException
import at.bitfire.dav4jvm.ktor.exception.HttpException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.DebugDirectory
import at.bitfire.davdroid.ui.composable.AppTheme
import at.bitfire.davdroid.ui.composable.CardWithImage
import at.bitfire.davdroid.ui.composable.ProgressBar
import at.bitfire.davdroid.ui.icon.Github
import java.io.File
import java.io.IOError
import java.io.IOException

@Composable
fun DebugInfoScreen(
    account: Account?,
    syncDataType: String?,
    cause: Throwable?,
    localResource: String?,
    canViewResource: Boolean,
    remoteResource: String?,
    debugLogFileName: DebugDirectory.FileName? = null,
    timestamp: Long?,
    onShareZipFile: (File) -> Unit,
    onViewFile: (File) -> Unit,
    onCopyRemoteUrl: () -> Unit,
    onViewLocalResource: () -> Unit,
    onNavUp: () -> Unit
) {
    val model: DebugInfoViewModel = hiltViewModel(
        creationCallback = { factory: DebugInfoViewModel.Factory ->
            factory.createWithDetails(DebugInfoViewModel.DebugInfoDetails(
                account = account,
                syncDataType = syncDataType,
                cause = cause,
                localResource = localResource,
                remoteResource = remoteResource,
                debugLogFileName = debugLogFileName,
                timestamp = timestamp
            ))
        }
    )

    val logFile = model.logFile
    val uiState = model.uiState
    val debugInfo = uiState.debugInfo
    val zipInProgress = uiState.zipInProgress
    val zipFile = uiState.zipFile
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
            is HttpException -> stringResource(if (cause.isServerError) R.string.debug_info_server_error else R.string.debug_info_http_error)
            is DavException -> stringResource(R.string.debug_info_webdav_error)
            is IOException, is IOError -> stringResource(R.string.debug_info_io_error)
            else -> cause?.let { it::class.java.simpleName }
        } ?: "",
        modelCauseSubtitle = cause?.localizedMessage,
        modelCauseMessage = stringResource(
            if (cause is HttpException)
                when {
                    cause.statusCode == 403 -> R.string.debug_info_http_403_description
                    cause.statusCode == 404 -> R.string.debug_info_http_404_description
                    cause.statusCode == 405 -> R.string.debug_info_http_405_description
                    cause.isServerError -> R.string.debug_info_http_5xx_description
                    else -> R.string.debug_info_unexpected_error
                }
            else
                R.string.debug_info_unexpected_error
        ),
        localResource = localResource,
        canViewResource = canViewResource,
        remoteResource = remoteResource,
        hasLogFile = logFile != null,
        onShareZip = { model.generateZip() },
        onViewLogsFile = { logFile?.let { onViewFile(it) } },
        onViewDebugFile = { debugInfo?.let { onViewFile(it) } },
        onCopyRemoteUrl = onCopyRemoteUrl,
        onViewLocalResource = onViewLocalResource,
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
    canViewResource: Boolean,
    remoteResource: String?,
    hasLogFile: Boolean,
    onShareZip: () -> Unit = {},
    onViewLogsFile: () -> Unit = {},
    onViewDebugFile: () -> Unit = {},
    onCopyRemoteUrl: () -> Unit = {},
    onViewLocalResource: () -> Unit = {},
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
                    title = stringResource(R.string.debug_info_privacy_warning_title),
                    message = stringResource(R.string.debug_info_privacy_warning_description),
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
                        remoteResource?.let { remoteUrl ->
                            Text(
                                text = stringResource(R.string.debug_info_involved_remote),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            SelectionContainer {
                                Text(
                                    text = remoteUrl,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                            OutlinedButton(
                                onClick = onCopyRemoteUrl,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(stringResource(R.string.debug_info_copy_remote_url))
                            }
                        }
                        localResource?.let {
                            Text(
                                text = stringResource(R.string.debug_info_involved_local),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        if (canViewResource)
                            OutlinedButton(
                                onClick = { onViewLocalResource() },
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Text(
                                    stringResource(R.string.debug_info_view_local_resource)
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

                FindHelpCard(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))

                // space for the FAB
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}

@Composable
private fun FindHelpCard(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current

    CardWithImage(
        title = stringResource(R.string.debug_info_find_help),
        icon = Icons.AutoMirrored.Rounded.LiveHelp,
        modifier = modifier
    ) {
        Text(
            text = AnnotatedString.fromHtml(stringResource(R.string.debug_info_find_help_faq))
        )
        OutlinedButton(
            onClick = { uriHandler.openUri("https://www.davx5.com/faq") },
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(stringResource(R.string.debug_info_find_help_faq_action))
        }

        Text(
            text = AnnotatedString.fromHtml(stringResource(R.string.debug_info_find_help_discussions))
        )
        OutlinedButton(
            onClick = { uriHandler.openUri("https://github.com/bitfireAT/davx5-ose/discussions") },
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(Github, stringResource(R.string.debug_info_find_help_discussions_action))
            Text(stringResource(R.string.debug_info_find_help_discussions_action), Modifier.padding(start = 4.dp))
        }

        Text(
            text = AnnotatedString.fromHtml(stringResource(R.string.debug_info_find_help_issues))
        )
        OutlinedButton(
            onClick = { uriHandler.openUri("https://github.com/bitfireAT/davx5-ose/issues") },
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(Github, stringResource(R.string.debug_info_find_help_issues_action))
            Text(stringResource(R.string.debug_info_find_help_issues_action), Modifier.padding(start = 4.dp))
        }

        Text(
            text = AnnotatedString.fromHtml(stringResource(R.string.debug_info_find_help_manual))
        )
        OutlinedButton(
            onClick = { uriHandler.openUri("https://manual.davx5.com/") },
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Text(stringResource(R.string.debug_info_find_help_manual_action))
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
        canViewResource = true,
        remoteResource = "remote-resource-string",
        hasLogFile = true
    )
}