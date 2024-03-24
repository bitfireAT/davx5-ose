package at.bitfire.davdroid.ui.setup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.os.CancellationSignal
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.R
import at.bitfire.davdroid.servicedetection.DavResourceFinder
import at.bitfire.davdroid.ui.DebugInfoActivity

@Composable
fun DetectResourcesPage(
    loginInfo: LoginInfo,
    onSuccess: (DavResourceFinder.Configuration) -> Unit,
    model: LoginModel = viewModel()
) {
    val cancellationSignal = remember { CancellationSignal() }
    DisposableEffect(Unit) {
        onDispose {
            cancellationSignal.cancel()
        }
    }

    LaunchedEffect(loginInfo) {
        model.detectResources(loginInfo, cancellationSignal)
    }

    val result by model.foundConfig.observeAsState()
    val foundSomething = result?.calDAV != null || result?.cardDAV != null
    val foundNothing = result?.calDAV == null && result?.cardDAV == null

    LaunchedEffect(result) {
        if (foundSomething)
            result?.let { onSuccess(it) }
    }

    if (result == null)
        DetectResourcesPage_InProgress()
    else if (foundNothing)
        DetectResourcesPage_NothingFound(
            encountered401 = result?.encountered401 ?: false,
            logs = result?.logs ?: ""
        )
}

@Composable
@Preview
fun DetectResourcesPage_InProgress() {
    Column(Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            color = MaterialTheme.colors.secondary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp))

        Column(Modifier.padding(8.dp)) {
            Text(
                stringResource(R.string.login_configuration_detection),
                style = MaterialTheme.typography.h5,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Text(
                stringResource(R.string.login_querying_server),
                style = MaterialTheme.typography.body1
            )
        }
    }
}

@Composable
fun DetectResourcesPage_NothingFound(
    encountered401: Boolean,
    logs: String
) {
    Column(Modifier.padding(8.dp)) {
        Text(
            stringResource(R.string.login_no_caldav_carddav),
            style = MaterialTheme.typography.body1
        )

        if (encountered401)
            Text(
                stringResource(R.string.login_username_password_wrong),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.body1
            )

        if (logs.isNotBlank()) {
            val context = LocalContext.current
            TextButton(
                onClick = {
                    val intent = DebugInfoActivity.IntentBuilder(context)
                        .withLogs(logs)
                        .build()
                    context.startActivity(intent)
                }
            ) {
                Text(stringResource(R.string.login_view_logs).uppercase())
            }
        }
    }
}

@Composable
@Preview
fun DetectResourcesPage_NothingFound() {
    DetectResourcesPage_NothingFound(
        encountered401 = false,
        logs = "SOME LOGS"
    )
}

@Composable
@Preview
fun DetectResourcesPage_NothingFound_401() {
    DetectResourcesPage_NothingFound(
        encountered401 = true,
        logs = ""
    )
}