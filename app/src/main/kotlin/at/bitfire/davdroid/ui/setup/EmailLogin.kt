/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
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

object EmailLogin : LoginType {

    override val title: Int
        get() = R.string.login_type_email

    override val helpUrl: Uri?
        get() = null


    @Composable
    override fun LoginScreen(
        snackbarHostState: SnackbarHostState,
        initialLoginInfo: LoginInfo,
        onLogin: (LoginInfo) -> Unit
    ) {
        val model: EmailLoginModel = hiltViewModel(
            creationCallback = { factory: EmailLoginModel.Factory ->
                factory.create(loginInfo = initialLoginInfo)
            }
        )

        val uiState = model.uiState
        EmailLoginScreen(
            email = uiState.email,
            onSetEmail = model::setEmail,
            password = uiState.password,
            onSetPassword = model::setPassword,
            canContinue = uiState.canContinue,
            onLogin = { onLogin(uiState.asLoginInfo()) }
        )
    }

}


@Composable
fun EmailLoginScreen(
    email: String,
    onSetEmail: (String) -> Unit = {},
    password: String,
    onSetPassword: (String) -> Unit = {},
    canContinue: Boolean,
    onLogin: () -> Unit = {}
) {
    val focusRequester = remember { FocusRequester() }

    Assistant(
        nextLabel = stringResource(R.string.login_login),
        nextEnabled = canContinue,
        onNext = onLogin
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(
                stringResource(R.string.login_type_email),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            val manualUrl = ExternalUris.Manual.baseUrl.buildUpon()
                .appendPath(ExternalUris.Manual.PATH_ACCOUNTS_COLLECTIONS)
                .fragment(ExternalUris.Manual.FRAGMENT_SERVICE_DISCOVERY)
                .build()
            val emailInfo = HtmlCompat.fromHtml(stringResource(R.string.login_email_address_info, manualUrl), HtmlCompat.FROM_HTML_MODE_COMPACT)
            Text(
                text = emailInfo.toAnnotatedString(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp)
            )

            OutlinedTextField(
                value = email,
                onValueChange = onSetEmail,
                label = { Text(stringResource(R.string.login_email_address)) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Email, null)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )

            PasswordTextField(
                password = password,
                onPasswordChange = onSetPassword,
                labelText = stringResource(R.string.login_password),
                leadingIcon = {
                    Icon(Icons.Default.Password, null)
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions {
                    if (canContinue) onLogin()
                },
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
fun EmailLoginScreen_Preview() {
    EmailLoginScreen(
        email = "test@example.com",
        password = "",
        canContinue = false
    )
}