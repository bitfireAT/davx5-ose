/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.ExternalUris
import at.bitfire.davdroid.ui.UiUtils.toAnnotatedString
import at.bitfire.davdroid.ui.composable.Assistant
import java.util.logging.Level
import java.util.logging.Logger

object OidcLogin : LoginType {

    override val title
        get() = R.string.login_type_oidc

    override val helpUrl: Uri?
        get() = null

    @Composable
    override fun LoginScreen(
        snackbarHostState: SnackbarHostState,
        initialLoginInfo: LoginInfo,
        onLogin: (LoginInfo) -> Unit
    ) {
        val model: OidcLoginViewModel = hiltViewModel(
            creationCallback = { factory: OidcLoginViewModel.Factory ->
                factory.create(loginInfo = initialLoginInfo)
            }
        )

        // contract to open the browser for authentication
        val authRequestContract = rememberLauncherForActivityResult(model.authorizationContract()) { authResponse ->
            if (authResponse != null)
                model.authenticate(authResponse)
            else
                model.authCodeFailed()
        }

        val uiState = model.uiState
        LaunchedEffect(uiState.authorizationRequest) {
            if (uiState.authorizationRequest != null) {
                try {
                    authRequestContract.launch(uiState.authorizationRequest)
                } catch (e: ActivityNotFoundException) {
                    Logger.getGlobal().log(Level.WARNING, "Couldn't start OAuth intent", e)
                    model.signInFailed()
                }
                model.resetResult()
            }
        }

        LaunchedEffect(uiState.result) {
            if (uiState.result != null) {
                onLogin(uiState.result)
                model.resetResult()
            }
        }

        LaunchedEffect(uiState.error) {
            if (uiState.error != null)
                snackbarHostState.showSnackbar(uiState.error)
        }

        OidcLoginScreen(
            url = uiState.url,
            onSetUrl = model::setUrl,
            clientId = uiState.clientId,
            onSetClientId = model::setClientId,
            scope = uiState.scope,
            onSetScope = model::setScope,
            canContinue = uiState.canContinue,
            onLogin = {
                if (uiState.canContinue) model.signIn()
            }
        )
    }

}

@Composable
fun OidcLoginScreen(
    url: String,
    onSetUrl: (String) -> Unit = {},
    clientId: String,
    onSetClientId: (String) -> Unit = {},
    scope: String,
    onSetScope: (String) -> Unit = {},
    canContinue: Boolean,
    onLogin: () -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }

    Assistant(
        nextLabel = stringResource(R.string.login_login),
        nextEnabled = canContinue,
        onNext = onLogin
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                stringResource(R.string.login_type_oidc),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            val manualUrl = ExternalUris.Manual.baseUrl.buildUpon()
                .appendPath(ExternalUris.Manual.PATH_ACCOUNTS_COLLECTIONS)
                .fragment(ExternalUris.Manual.FRAGMENT_SERVICE_DISCOVERY)
                .build()
            val urlInfo = HtmlCompat.fromHtml(
                stringResource(R.string.login_base_url_info, manualUrl),
                HtmlCompat.FROM_HTML_MODE_COMPACT
            )
            Text(
                text = urlInfo.toAnnotatedString(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp)
            )

            OutlinedTextField(
                value = url,
                onValueChange = onSetUrl,
                label = { Text(stringResource(R.string.login_base_url)) },
                placeholder = { Text("dav.example.com/path") },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Folder, null)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )

            OutlinedTextField(
                value = clientId,
                onValueChange = onSetClientId,
                label = { Text(stringResource(R.string.login_oidc_client_id)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = scope,
                onValueChange = onSetScope,
                label = { Text(stringResource(R.string.login_oidc_scope)) },
                placeholder = { Text("openid profile") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
@Preview
fun OidcLoginScreen_Preview() {
    OidcLoginScreen(
        url = "https://example.com",
        clientId = "a1b2c3",
        scope = "openid profile",
        canContinue = false
    )
}