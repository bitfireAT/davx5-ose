/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.GoogleOAuth
import at.bitfire.davdroid.ui.UiUtils
import com.google.accompanist.themeadapter.material.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.TokenResponse
import org.apache.commons.lang3.StringUtils
import java.net.URI
import javax.inject.Inject

@AndroidEntryPoint
class GoogleLoginFragment: Fragment() {

    companion object {

        val URI_TESTED_WITH_GOOGLE: Uri = Uri.parse("https://www.davx5.com/tested-with/google")

        fun googleBaseUri(googleAccount: String): URI =
            URI("https", "www.google.com", "/calendar/dav/$googleAccount/events/", null)

    }

    private val loginModel by activityViewModels<LoginModel>()
    private val model by viewModels<Model>()

    private val authRequestContract = registerForActivityResult(object: ActivityResultContract<AuthorizationRequest, AuthorizationResponse?>() {
        override fun createIntent(context: Context, input: AuthorizationRequest) =
            model.authService.getAuthorizationRequestIntent(input)

        override fun parseResult(resultCode: Int, intent: Intent?): AuthorizationResponse? =
            intent?.let { AuthorizationResponse.fromIntent(it) }
    }) { authResponse ->
        if (authResponse != null)
            model.authenticate(authResponse)
        else
            Logger.log.warning("Couldn't obtain authorization code")
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = ComposeView(requireActivity()).apply {
            setContent {
                GoogleLogin(onLogin = { account, clientId ->
                    loginModel.baseURI = googleBaseUri(account)

                    val authRequest = GoogleOAuth.authRequestBuilder(clientId)
                        .setScopes(*GoogleOAuth.SCOPES)
                        .setLoginHint(account)
                        .build()
                    authRequestContract.launch(authRequest)
                })
            }
        }

        model.credentials.observe(viewLifecycleOwner) { credentials ->
            if (credentials != null) {
                // pass credentials to login model
                loginModel.credentials = credentials

                // continue with service detection
                parentFragmentManager.beginTransaction()
                    .replace(android.R.id.content, DetectConfigurationFragment(), null)
                    .addToBackStack(null)
                    .commit()

                // reset because setting credentials LiveData represents a one-shot action
                model.credentials.value = null
            }
        }

        return view
    }


    @HiltViewModel
    class Model @Inject constructor(
        application: Application,
        val authService: AuthorizationService
    ): AndroidViewModel(application) {

        val credentials = MutableLiveData<Credentials>()

        fun authenticate(resp: AuthorizationResponse) = viewModelScope.launch(Dispatchers.IO) {
            val authState = AuthState(resp, null)       // authorization code must not be stored; exchange it to refresh token

            authService.performTokenRequest(resp.createTokenExchangeRequest()) { tokenResponse: TokenResponse?, refreshTokenException: AuthorizationException? ->
                Logger.log.info("Refresh token response: ${tokenResponse?.jsonSerializeString()}")
                if (tokenResponse != null) {
                    // success
                    authState.update(tokenResponse, refreshTokenException)
                    // save authState (= refresh token)
                    credentials.postValue(Credentials(authState = authState))
                }
            }
        }

        override fun onCleared() {
            authService.dispose()
        }

    }

}


@Composable
fun GoogleLogin(
    onLogin: (account: String, clientId: String?) -> Unit
) {
    val context = LocalContext.current
    MdcTheme {
        Column(
            Modifier
                .padding(8.dp)
                .verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.login_type_google),
                style = MaterialTheme.typography.h5,
                modifier = Modifier.padding(vertical = 16.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    Row {
                        Image(Icons.Default.Warning, contentDescription = "", modifier = Modifier.padding(top = 8.dp, end = 8.dp, bottom = 8.dp))
                        Text(stringResource(R.string.login_google_see_tested_with))
                    }
                    Text(stringResource(R.string.login_google_unexpected_warnings), modifier = Modifier.padding(vertical = 8.dp))
                    Button(
                        onClick = {
                            UiUtils.launchUri(context, GoogleLoginFragment.URI_TESTED_WITH_GOOGLE)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(),
                        modifier = Modifier.wrapContentSize()
                    ) {
                        Text(stringResource(R.string.intro_more_info))
                    }
                }
            }

            val email = rememberSaveable { mutableStateOf("") }
            val emailError = remember { mutableStateOf(false) }
            OutlinedTextField(
                email.value,
                singleLine = true,
                onValueChange = { email.value = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                label = { Text(stringResource(R.string.login_google_account)) },
                isError = emailError.value,
                placeholder = { Text("example@gmail.com") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            val userClientId = rememberSaveable { mutableStateOf("") }
            val userClientIdError = remember { mutableStateOf(false) }
            OutlinedTextField(
                userClientId.value,
                singleLine = true,
                onValueChange = { clientId ->
                    userClientId.value = clientId
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                label = { Text(stringResource(R.string.login_google_client_id)) },
                isError = userClientIdError.value,
                placeholder = { Text("[...].apps.googleusercontent.com") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            Button({
                val validEmail = email.value.contains('@')
                emailError.value = !validEmail

                if (validEmail) {
                    val clientId = StringUtils.trimToNull(userClientId.value.trim())
                    onLogin(email.value, clientId)
                }
            }, modifier = Modifier
                .padding(top = 8.dp)
                .wrapContentSize()) {
                Text(stringResource(R.string.login_login))
            }

            Text(
                stringResource(R.string.login_google_disclaimer, stringResource(R.string.app_name)),
                style = MaterialTheme.typography.body2,
                modifier = Modifier.padding(top = 24.dp))

        }
    }
}

@Composable
@Preview
fun PreviewGoogleLogin() {
    GoogleLogin(onLogin = { _, _ -> })
}