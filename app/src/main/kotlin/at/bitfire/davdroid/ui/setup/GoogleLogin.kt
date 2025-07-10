/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.hilt.navigation.compose.hiltViewModel
import at.bitfire.davdroid.R
import at.bitfire.davdroid.ui.ExternalUris
import at.bitfire.davdroid.ui.ExternalUris.withStatParams
import at.bitfire.davdroid.ui.UiUtils.toAnnotatedString
import at.bitfire.davdroid.ui.setup.GoogleLogin.GOOGLE_POLICY_URL
import java.util.logging.Level
import java.util.logging.Logger

object GoogleLogin : LoginType {

    override val title: Int
        get() = R.string.login_type_google

    override val helpUrl: Uri
        get() = ExternalUris.Homepage.baseUrl.buildUpon()
            .appendPath(ExternalUris.Homepage.PATH_TESTED_SERVICES)
            .appendPath("google")
            .withStatParams("LoginTypeGoogle")
            .build()


    // Google API Services User Data Policy
    const val GOOGLE_POLICY_URL =
        "https://developers.google.com/terms/api-services-user-data-policy#additional_requirements_for_specific_api_scopes"


    @Composable
    override fun LoginScreen(
        snackbarHostState: SnackbarHostState,
        initialLoginInfo: LoginInfo,
        onLogin: (LoginInfo) -> Unit
    ) {
        val model: GoogleLoginModel = hiltViewModel(
            creationCallback = { factory: GoogleLoginModel.Factory ->
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

        GoogleLoginScreen(
            email = uiState.email,
            onSetEmail = model::setEmail,
            customClientId = uiState.customClientId,
            onSetCustomClientId = model::setCustomClientId,
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
fun GoogleLoginScreen(
    email: String,
    onSetEmail: (String) -> Unit = {},
    customClientId: String,
    onSetCustomClientId: (String) -> Unit = {},
    canContinue: Boolean,
    onLogin: () -> Unit = {}
) {
    val context = LocalContext.current

    Column(
        Modifier
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            stringResource(R.string.login_type_google),
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
            label = { Text(stringResource(R.string.login_google_account)) },
            placeholder = { Text("example@gmail.com") },
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

        OutlinedTextField(
            customClientId,
            singleLine = true,
            onValueChange = onSetCustomClientId,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onLogin() }
            ),
            label = { Text(stringResource(R.string.login_google_client_id)) },
            placeholder = { Text("[...].apps.googleusercontent.com") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )

        Button(
            enabled = canContinue,
            onClick = { onLogin() },
            modifier = Modifier
                .padding(top = 8.dp)
                .wrapContentSize()
        ) {
            Image(
                painter = painterResource(R.drawable.google_g_logo),
                contentDescription = stringResource(R.string.login_google),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = stringResource(R.string.login_google),
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        Spacer(Modifier.padding(8.dp))

        val privacyPolicyUrl = ExternalUris.Homepage.baseUrl.buildUpon()
            .appendPath(ExternalUris.Homepage.PATH_PRIVACY)
            .withStatParams("GoogleLoginFragment")
            .build()
        val privacyPolicyNote = HtmlCompat.fromHtml(
            stringResource(
                R.string.login_google_client_privacy_policy,
                context.getString(R.string.app_name),
                privacyPolicyUrl.toString()
            ), 0
        ).toAnnotatedString()
        Text(
            text = privacyPolicyNote,
            style = MaterialTheme.typography.bodyMedium
        )

        val limitedUseNote = HtmlCompat.fromHtml(
            stringResource(R.string.login_google_client_limited_use, context.getString(R.string.app_name), GOOGLE_POLICY_URL), 0
        ).toAnnotatedString()
        Text(
            text = limitedUseNote,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
@Preview(showBackground = true)
fun GoogleLoginScreen_Preview_Empty() {
    GoogleLoginScreen(
        email = "",
        customClientId = "",
        canContinue = false
    )
}

@Composable
@Preview(showBackground = true)
fun GoogleLoginScreen_Preview_WithDefaultEmail() {
    GoogleLoginScreen(
        email = "example@gmail.com",
        customClientId = "some-client-id",
        canContinue = true
    )
}