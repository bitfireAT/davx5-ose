/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Password
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
import androidx.hilt.navigation.compose.hiltViewModel
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.ExternalUris
import at.bitfire.davdroid.ui.UiUtils.toAnnotatedString
import at.bitfire.davdroid.ui.composable.Assistant
import at.bitfire.davdroid.ui.composable.PasswordTextField
import at.bitfire.davdroid.ui.composable.SelectClientCertificateCard

object AdvancedLogin : LoginType {

    override val title: Int
        get() = R.string.login_type_advanced

    override val helpUrl: Uri?
        get() = null


    @Composable
    override fun LoginScreen(
        snackbarHostState: SnackbarHostState,
        initialLoginInfo: LoginInfo,
        onLogin: (LoginInfo) -> Unit
    ) {
        val model: AdvancedLoginModel = hiltViewModel(
            creationCallback = { factory: AdvancedLoginModel.Factory ->
                factory.create(loginInfo = initialLoginInfo)
            }
        )

        val uiState = model.uiState
        AdvancedLoginScreen(
            snackbarHostState = snackbarHostState,
            url = uiState.url,
            onSetUrl = model::setUrl,
            username = uiState.username,
            onSetUsername = model::setUsername,
            password = uiState.password,
            certAlias = uiState.certAlias,
            onSetCertAlias = model::setCertAlias,
            canContinue = uiState.canContinue,
            onLogin = {
                onLogin(uiState.asLoginInfo())
            }
        )
    }

}

@Composable
fun AdvancedLoginScreen(
    snackbarHostState: SnackbarHostState,
    url: String,
    onSetUrl: (String) -> Unit = {},
    username: String,
    onSetUsername: (String) -> Unit = {},
    password: TextFieldState,
    certAlias: String,
    onSetCertAlias: (String) -> Unit = {},
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
                stringResource(R.string.login_type_advanced),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            val manualUrl = ExternalUris.Manual.baseUrl.buildUpon()
                .appendPath(ExternalUris.Manual.PATH_ACCOUNTS_COLLECTIONS)
                .fragment(ExternalUris.Manual.FRAGMENT_SERVICE_DISCOVERY)
                .build()
            val urlInfo = HtmlCompat.fromHtml(stringResource(R.string.login_base_url_info, manualUrl), HtmlCompat.FROM_HTML_MODE_COMPACT)
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
                value = username,
                onValueChange = onSetUsername,
                label = { Text(stringResource(R.string.login_user_name_optional)) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.AccountCircle, null)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            PasswordTextField(
                password = password,
                labelText = stringResource(R.string.login_password_optional),
                leadingIcon = {
                    Icon(Icons.Default.Password, null)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            SelectClientCertificateCard(
                snackbarHostState = snackbarHostState,
                suggestedAlias = null,
                chosenAlias = certAlias,
                onAliasChosen = onSetCertAlias
            )
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
@Preview
fun AdvancedLoginScreen_Preview_Empty() {
    AdvancedLoginScreen(
        snackbarHostState = SnackbarHostState(),
        url = "",
        username = "",
        password = rememberTextFieldState(""),
        certAlias = "",
        canContinue = false
    )
}

@Composable
@Preview
fun AdvancedLoginScreen_Preview_AllFilled() {
    AdvancedLoginScreen(
        snackbarHostState = SnackbarHostState(),
        url = "dav.example.com",
        username = "someuser",
        password = rememberTextFieldState("password"),
        certAlias = "someCert",
        canContinue = true
    )
}