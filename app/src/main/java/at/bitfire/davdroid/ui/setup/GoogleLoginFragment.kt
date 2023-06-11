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
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.TokenResponse
import java.net.URI

class GoogleLoginFragment: Fragment() {

    companion object {
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
        val v = ComposeView(requireActivity())
        v.setContent {
            GoogleLogin()
        }

        model.credentials.observe(viewLifecycleOwner) { credentials ->
            loginModel.credentials = credentials

            // continue with service detection
            parentFragmentManager.beginTransaction()
                .replace(android.R.id.content, DetectConfigurationFragment(), null)
                .addToBackStack(null)
                .commit()
        }

        return v
    }


    @Composable
    @Preview
    fun GoogleLogin() {
        MdcTheme {
            Column(Modifier.padding(8.dp).verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.login_type_google),
                    style = MaterialTheme.typography.h5,
                    modifier = Modifier.padding(vertical = 16.dp))

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        Text(
                            stringResource(R.string.login_google_guide),
                            modifier = Modifier.padding(vertical = 8.dp))
                        Button(
                            onClick = {
                                UiUtils.launchUri(requireActivity(), Uri.parse("https://www.davx5.com/tested-with/google"))
                            },
                            colors = ButtonDefaults.outlinedButtonColors(),
                            modifier = Modifier.wrapContentSize()
                        ) {
                            Text(stringResource(R.string.intro_more_info))
                        }
                    }
                }

                val email = remember { mutableStateOf("") }
                val emailError = remember { mutableStateOf<Boolean>(false) }
                OutlinedTextField(
                    email.value,
                    singleLine = true,
                    onValueChange = { account ->
                        email.value = account
                        loginModel.baseURI = googleBaseUri(account)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    label = { Text(stringResource(R.string.login_google_account)) },
                    isError = emailError.value,
                    placeholder = { Text("example@gmail.com") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                )

                Button({
                    val valid = email.value.orEmpty().contains('@')
                    emailError.value = !valid

                    if (valid) {
                        val authRequest = GoogleOAuth.authRequestBuilder()
                            .setScopes(*GoogleOAuth.SCOPES)
                            .setLoginHint(email.value)
                            .build()
                        authRequestContract.launch(authRequest)
                    }
                }, modifier = Modifier.wrapContentSize()) {
                    Text(stringResource(R.string.login_login))
                }

                Text(
                    stringResource(R.string.login_google_disclaimer, getString(R.string.app_name)),
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(top = 24.dp))

            }
        }
    }


    class Model(application: Application): AndroidViewModel(application) {

        val authService = GoogleOAuth.createAuthService(getApplication())
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