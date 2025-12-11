/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Browser
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.hilt.navigation.compose.hiltViewModel
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.ExternalUris
import at.bitfire.davdroid.ui.ExternalUris.withStatParams
import at.bitfire.davdroid.ui.UiUtils.haveCustomTabs
import at.bitfire.davdroid.ui.composable.Assistant
import at.bitfire.davdroid.ui.composable.ProgressBar
import kotlinx.coroutines.launch

object NextcloudLogin : LoginType {

    override val title: Int
        get() = R.string.login_type_nextcloud

    override val helpUrl: Uri
        get() = ExternalUris.Homepage.baseUrl.buildUpon()
            .appendPath(ExternalUris.Homepage.PATH_TESTED_SERVICES)
            .appendPath("nextcloud")
            .withStatParams(javaClass.simpleName)
            .build()


    @Composable
    override fun LoginScreen(
        snackbarHostState: SnackbarHostState,
        initialLoginInfo: LoginInfo,
        onLogin: (LoginInfo) -> Unit
    ) {
        val model: NextcloudLoginModel = hiltViewModel(
            creationCallback = { factory: NextcloudLoginModel.Factory ->
                factory.create(loginInfo = initialLoginInfo)
            }
        )

        val context = LocalContext.current
        val checkResultCallback = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            model.onReturnFromBrowser()
        }

        val uiState = model.uiState
        LaunchedEffect(uiState.loginUrl) {
            if (uiState.loginUrl != null) {
                val loginUri = uiState.loginUrl.toString().toUri()

                if (haveCustomTabs(context)) {
                    // Custom Tabs are available
                    @Suppress("DEPRECATION")
                    val browser = CustomTabsIntent.Builder()
                        .build()
                    browser.intent.data = loginUri
                    browser.intent.putExtra(
                        Browser.EXTRA_HEADERS,
                        bundleOf("Accept-Language" to Locale.current.toLanguageTag())
                    )
                    checkResultCallback.launch(browser.intent)
                } else {
                    // fallback: launch normal browser
                    val browser = Intent(Intent.ACTION_VIEW, loginUri)
                    browser.addCategory(Intent.CATEGORY_BROWSABLE)
                    if (browser.resolveActivity(context.packageManager) != null) {
                        checkResultCallback.launch(browser)
                    } else
                        this@LaunchedEffect.launch {
                            @SuppressLint("LocalContextGetResourceValueCall")
                            snackbarHostState.showSnackbar(context.getString(R.string.install_browser))
                        }
                }
            }
        }

        // continue to resource detection when result is set in model
        LaunchedEffect(uiState.result) {
            if (uiState.result != null) {
                onLogin(uiState.result)
                model.resetResult()
            }
        }

        NextcloudLoginScreen(
            baseUrl = uiState.baseUrl,
            onUpdateBaseUrl = { model.updateBaseUrl(it) },
            canContinue = uiState.canContinue,
            inProgress = uiState.inProgress,
            error = uiState.error,
            onLogin = { model.startLoginFlow() }
        )
    }

}

@Composable
fun NextcloudLoginScreen(
    baseUrl: String,
    onUpdateBaseUrl: (String) -> Unit = {},
    canContinue: Boolean,
    inProgress: Boolean,
    error: String? = null,
    onLogin: () -> Unit = {}
) {
    Assistant(
        nextLabel = stringResource(R.string.login_login),
        nextEnabled = canContinue,
        onNext = onLogin
    ) {
        if (inProgress)
            ProgressBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )

        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                stringResource(R.string.login_nextcloud_login_with_nextcloud),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Column {
                Text(
                    stringResource(R.string.login_nextcloud_login_flow_text),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )

                val focusRequester = remember { FocusRequester() }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = onUpdateBaseUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .focusRequester(focusRequester),
                    enabled = !inProgress,
                    leadingIcon = {
                        Icon(Icons.Default.Cloud, null)
                    },
                    label = {
                        Text(stringResource(R.string.login_nextcloud_login_flow_server_address))
                    },
                    placeholder = { Text("cloud.example.com") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onLogin() }
                    ),
                    singleLine = true
                )
                LaunchedEffect(Unit) {
                    if (baseUrl.isEmpty())
                        focusRequester.requestFocus()
                }

                if (error != null)
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(
                                error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
            }
        }
    }
}

@Composable
@Preview
fun NextcloudLoginScreen_Preview() {
    NextcloudLoginScreen(
        baseUrl = "cloud.example.com",
        canContinue = true,
        inProgress = false,
        error = null
    )
}

@Composable
@Preview
fun NextcloudLoginScreen_Preview_InProgressError() {
    NextcloudLoginScreen(
        baseUrl = "cloud.example.com",
        canContinue = true,
        inProgress = true,
        error = "Some Error"
    )
}