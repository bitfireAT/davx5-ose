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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.ExternalUris
import at.bitfire.davdroid.ui.ExternalUris.withStatParams
import java.util.logging.Level
import java.util.logging.Logger

object FastmailLogin : LoginType {

    override val title: Int
        get() = R.string.login_fastmail

    override val helpUrl: Uri
        get() = ExternalUris.Homepage.baseUrl.buildUpon()
            .appendPath(ExternalUris.Homepage.PATH_TESTED_SERVICES)
            .appendPath("fastmail")
            .withStatParams(screen = "LoginTypeFastmail")
            .build()


    @Composable
    override fun LoginScreen(
        snackbarHostState: SnackbarHostState,
        initialLoginInfo: LoginInfo,
        onLogin: (LoginInfo) -> Unit
    ) {
        val model: FastmailLoginModel = hiltViewModel(
            creationCallback = { factory: FastmailLoginModel.Factory ->
                factory.create(loginInfo = initialLoginInfo)
            }
        )

        val uiState = model.uiState
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

        // contract to open the browser for authentication
        val authRequestContract = rememberLauncherForActivityResult(model.authorizationContract()) { authResponse ->
            if (authResponse != null)
                model.authenticate(authResponse)
            else
                model.authCodeFailed()
        }

        FastmailLoginScreen(
            email = uiState.email,
            onSetEmail = model::setEmail,
            canContinue = uiState.canContinue,
            onLogin = {
                if (uiState.canContinue) {
                    val authRequest = model.signIn()

                    try {
                        authRequestContract.launch(authRequest)
                    } catch (e: ActivityNotFoundException) {
                        Logger.getGlobal().log(Level.WARNING, "Couldn't start OAuth intent", e)
                        model.signInFailed()
                    }
                }
            }
        )
    }
}

@Composable
fun FastmailLoginScreen(
    email: String,
    onSetEmail: (String) -> Unit = {},
    canContinue: Boolean,
    onLogin: () -> Unit = {}
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    Column(
        Modifier
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            stringResource(R.string.login_fastmail),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        val focusRequester = remember { FocusRequester() }
        OutlinedTextField(
            email,
            singleLine = true,
            onValueChange = onSetEmail,
            leadingIcon = {
                Icon(Icons.Default.Email, null)
            },
            label = { Text(stringResource(R.string.login_fastmail_account)) },
            placeholder = { Text("example@fastmail.com") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .focusRequester(focusRequester)
        )
        LaunchedEffect(Unit) {
            if (email.isEmpty())
                focusRequester.requestFocus()
        }

        Button(
            enabled = canContinue,
            onClick = { onLogin() },
            modifier = Modifier
                .padding(top = 8.dp)
                .wrapContentSize()
        ) {
            Text(stringResource(R.string.login_fastmail_sign_in))
        }
    }
}

@Composable
@Preview(showBackground = true)
fun FastmailLoginScreen_Preview_Empty() {
    FastmailLoginScreen(
        email = "",
        canContinue = false
    )
}

@Composable
@Preview(showBackground = true)
fun FastmailLoginScreen_Preview_WithDefaultEmail() {
    FastmailLoginScreen(
        email = "example@gmail.com",
        canContinue = true
    )
}