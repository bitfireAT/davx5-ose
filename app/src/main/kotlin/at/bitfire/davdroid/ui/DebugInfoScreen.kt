package at.bitfire.davdroid.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adb
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.composable.BasicTopAppBar
import at.bitfire.davdroid.ui.composable.CardWithImage
import java.io.File
import java.io.IOError
import java.io.IOException

@Composable
fun DebugInfoScreen(
    model: DebugInfoModel,
    onShareFile: (File) -> Unit,
    onViewFile: (File) -> Unit,
    onNavUp: () -> Unit
) {
    val debugInfo by model.debugInfo.observeAsState()
    val zipProgress by model.zipProgress.observeAsState(false)
    val modelCause by model.cause.observeAsState()
    val localResource by model.localResource.observeAsState()
    val remoteResource by model.remoteResource.observeAsState()
    val logFile by model.logFile.observeAsState()

    AppTheme {
        DebugInfoScreen(
            model,
            debugInfo,
            zipProgress,
            modelCause,
            localResource,
            remoteResource,
            logFile,
            onShareFile = { onShareFile(it) },
            onViewFile = { onViewFile(it) },
            onNavUp
        )
    }
}

@Composable
fun DebugInfoScreen(
    model: DebugInfoModel,
    debugInfo: File?,
    zipProgress: Boolean,
    modelCause: Throwable?,
    localResource: String?,
    remoteResource: String?,
    logFile: File?,
    onShareFile: (File) -> Unit,
    onViewFile: (File) -> Unit,
    onNavUp: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        floatingActionButton = {
            if (debugInfo != null && !zipProgress) {
                FloatingActionButton(
                    onClick = model::generateZip
                ) {
                    Icon(Icons.Rounded.Share, stringResource(R.string.share))
                }
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
            BasicTopAppBar(
                titleStringRes = R.string.debug_info_title,
                onNavigateUp = { onNavUp() }
            )
        }
    ) { paddingValues ->
        val error by model.error.observeAsState()
        LaunchedEffect(error) {
            error?.let {
                snackbarHostState.showSnackbar(
                    message = it,
                    duration = SnackbarDuration.Long
                )

                model.error.value = null
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (debugInfo == null)
                item {
                    LinearProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                }

            if (debugInfo != null) {
                if (zipProgress)
                    item {
                        LinearProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                    }

                item {
                    CardWithImage(
                        image = painterResource(R.drawable.undraw_server_down),
                        imageAlignment = BiasAlignment(0f, .7f),
                        title = stringResource(R.string.debug_info_archive_caption),
                        subtitle = stringResource(R.string.debug_info_archive_subtitle),
                        message = stringResource(R.string.debug_info_archive_text),
                        icon = Icons.Rounded.Share,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        TextButton(
                            onClick = model::generateZip,
                            enabled = !zipProgress
                        ) {
                            Text(
                                stringResource(R.string.debug_info_archive_share).uppercase()
                            )
                        }
                    }
                }
            }
            modelCause?.let { cause ->
                item {
                    CardWithImage(
                        title = when (cause) {
                            is HttpException -> stringResource(if (cause.code / 100 == 5) R.string.debug_info_server_error else R.string.debug_info_http_error)
                            is DavException -> stringResource(R.string.debug_info_webdav_error)
                            is IOException, is IOError -> stringResource(R.string.debug_info_io_error)
                            else -> cause::class.java.simpleName
                        },
                        subtitle = cause.localizedMessage,
                        message = stringResource(
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
                        icon = Icons.Rounded.Info,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        TextButton(
                            enabled = debugInfo != null,
                            onClick = { onViewFile(debugInfo!!) }
                        ) {
                            Text(
                                stringResource(R.string.debug_info_view_details).uppercase()
                            )
                        }
                    }
                }
            }
            debugInfo?.let { info ->
                item {
                    CardWithImage(
                        title = stringResource(R.string.debug_info_title),
                        subtitle = stringResource(R.string.debug_info_subtitle),
                        icon = Icons.Rounded.BugReport,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        TextButton(
                            onClick = { onViewFile(info) }
                        ) {
                            Text(
                                stringResource(R.string.debug_info_view_details).uppercase()
                            )
                        }
                    }
                }
            }
            if (localResource != null || remoteResource != null) item {
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
                        Text(
                            text = it,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    localResource?.let {
                        Text(
                            text = stringResource(R.string.debug_info_involved_local),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = it,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }
            logFile?.let { logs ->
                item {
                    CardWithImage(
                        title = stringResource(R.string.debug_info_logs_caption),
                        subtitle = stringResource(R.string.debug_info_logs_subtitle),
                        icon = Icons.Rounded.BugReport,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        TextButton(
                            onClick = { onShareFile(logs) }
                        ) {
                            Text(
                                stringResource(R.string.debug_info_logs_view).uppercase()
                            )
                        }
                    }
                }
            }
        }
    }
}