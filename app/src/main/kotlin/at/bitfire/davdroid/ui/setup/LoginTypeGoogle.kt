/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.accounts.AccountManager
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.SnackbarHostState
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.Constants.withStatParams
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.UiUtils.toAnnotatedString
import at.bitfire.davdroid.ui.setup.LoginTypeGoogle.GOOGLE_POLICY_URL
import at.bitfire.davdroid.ui.widget.ClickableTextWithLink
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import org.apache.commons.lang3.StringUtils
import java.net.URI
import java.util.logging.Level
import javax.inject.Inject

object LoginTypeGoogle : LoginType {

    override val title: Int
        get() = R.string.login_type_google

    override val helpUrl: Uri
        get() = Constants.HOMEPAGE_URL.buildUpon()
            .appendPath(Constants.HOMEPAGE_PATH_TESTED_SERVICES)
            .appendPath("google")
            .withStatParams("LoginTypeGoogle")
            .build()


    // Google API Services User Data Policy
    const val GOOGLE_POLICY_URL =
        "https://developers.google.com/terms/api-services-user-data-policy#additional_requirements_for_specific_api_scopes"

    // Support site
    val URI_TESTED_WITH_GOOGLE: Uri =
        Constants.HOMEPAGE_URL.buildUpon()
            .appendPath(Constants.HOMEPAGE_PATH_TESTED_SERVICES)
            .appendPath("google")
            .build()

    // davx5integration@gmail.com (for davx5-ose)
    private const val CLIENT_ID = "1069050168830-eg09u4tk1cmboobevhm4k3bj1m4fav9i.apps.googleusercontent.com"

    val SCOPES = arrayOf(
        "https://www.googleapis.com/auth/calendar",     // CalDAV
        "https://www.googleapis.com/auth/carddav"       // CardDAV
    )

    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
        Uri.parse("https://oauth2.googleapis.com/token")
    )

    fun authRequestBuilder(clientId: String?) =
        AuthorizationRequest.Builder(
            serviceConfig,
            clientId ?: CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(BuildConfig.APPLICATION_ID + ":/oauth2/redirect")
        )

    /**
     * Gets the Google CalDAV/CardDAV base URI. See https://developers.google.com/calendar/caldav/v2/guide;
     * _calid_ of the primary calendar is the account name.
     *
     * This URL allows CardDAV (over well-known URLs) and CalDAV detection including calendar-homesets and secondary
     * calendars.
     */
    fun googleBaseUri(googleAccount: String): URI =
        URI("https", "apidata.googleusercontent.com", "/caldav/v2/$googleAccount/user", null)


    @Composable
    override fun Content(
        snackbarHostState: SnackbarHostState,
        loginInfo: LoginInfo,
        onUpdateLoginInfo: (newLoginInfo: LoginInfo) -> Unit,
        onDetectResources: () -> Unit,
        onFinish: () -> Unit
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val model: Model = viewModel()

        val authRequestContract = rememberLauncherForActivityResult(contract = AuthorizationContract(model)) { authResponse ->
            if (authResponse != null)
                model.authenticate(authResponse)
            else
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.login_oauth_couldnt_obtain_auth_code))
                }
        }

        model.credentials.observeAsState().value?.let { credentials ->
            onUpdateLoginInfo(loginInfo.copy(credentials = credentials))
            onDetectResources()
        }

        GoogleLoginScreen(
            defaultEmail = loginInfo.credentials?.username ?: model.findGoogleAccount(),
            onLogin = { accountEmail, clientId ->
                onUpdateLoginInfo(
                    LoginInfo(
                        baseUri = googleBaseUri(accountEmail),
                        suggestedAccountName = accountEmail
                    )
                )

                val authRequest = authRequestBuilder(clientId)
                    .setScopes(*SCOPES)
                    .setLoginHint(accountEmail)
                    .setUiLocales(Locale.current.toLanguageTag())
                    .build()

                try {
                    authRequestContract.launch(authRequest)
                } catch (e: ActivityNotFoundException) {
                    Logger.log.log(Level.WARNING, "Couldn't start OAuth intent", e)
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.install_browser))
                    }
                }
            }
        )
    }


    class AuthorizationContract(val model: Model) : ActivityResultContract<AuthorizationRequest, AuthorizationResponse?>() {
        override fun createIntent(context: Context, input: AuthorizationRequest) =
            model.authService.getAuthorizationRequestIntent(input)

        override fun parseResult(resultCode: Int, intent: Intent?): AuthorizationResponse? =
            intent?.let { AuthorizationResponse.fromIntent(it) }
    }


    @HiltViewModel
    class Model @Inject constructor(
        val context: Application,
        val authService: AuthorizationService
    ) : ViewModel() {

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

        fun findGoogleAccount(): String? {
            val accountManager = AccountManager.get(context)
            return accountManager
                .getAccountsByType("com.google")
                .map { it.name }
                .firstOrNull()
        }

        override fun onCleared() {
            authService.dispose()
        }

    }

}

@OptIn(ExperimentalTextApi::class)
@Composable
fun GoogleLoginScreen(
    defaultEmail: String?,
    onLogin: (accountEmail: String, clientId: String?) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    Column(
        Modifier
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            stringResource(R.string.login_type_google),
            style = MaterialTheme.typography.h5,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(8.dp)) {
                Row {
                    Text(
                        stringResource(R.string.login_google_see_tested_with),
                        style = MaterialTheme.typography.body2,
                    )
                }
                Text(
                    stringResource(R.string.login_google_unexpected_warnings),
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Button(
                    onClick = {
                        uriHandler.openUri(LoginTypeGoogle.URI_TESTED_WITH_GOOGLE.toString())
                    },
                    colors = ButtonDefaults.outlinedButtonColors(),
                    modifier = Modifier.wrapContentSize()
                ) {
                    Text(stringResource(R.string.intro_more_info))
                }
            }
        }

        var email by rememberSaveable { mutableStateOf(defaultEmail ?: "") }
        var userClientId by rememberSaveable { mutableStateOf("") }
        var emailError: String? by rememberSaveable { mutableStateOf(null) }

        fun login() {
            val userEmail: String? = StringUtils.trimToNull(email.trim())
            val clientId: String? = StringUtils.trimToNull(userClientId.trim())
            if (userEmail.isNullOrBlank()) {
                emailError = context.getString(R.string.login_email_address_error)
                return
            }

            // append @gmail.com, if necessary
            val loginEmail =
                if (userEmail.contains('@'))
                    userEmail
                else
                    "$userEmail@gmail.com"

            onLogin(loginEmail, clientId)
        }

        val focusRequester = remember { FocusRequester() }
        OutlinedTextField(
            email,
            singleLine = true,
            onValueChange = { emailError = null; email = it },
            leadingIcon = {
                Icon(Icons.Default.Email, null)
            },
            isError = emailError != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            label = { Text(emailError ?: stringResource(R.string.login_google_account)) },
            placeholder = { Text("example@gmail.com") },
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
            userClientId,
            singleLine = true,
            onValueChange = { clientId ->
                userClientId = clientId
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { login() }
            ),
            label = { Text(stringResource(R.string.login_google_client_id)) },
            placeholder = { Text("[...].apps.googleusercontent.com") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )

        Button(
            onClick = { login() },
            modifier = Modifier
                .padding(top = 8.dp)
                .wrapContentSize(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = MaterialTheme.colors.surface
            )
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

        val privacyPolicyUrl = Constants.HOMEPAGE_URL.buildUpon()
            .appendPath(Constants.HOMEPAGE_PATH_PRIVACY)
            .withStatParams("GoogleLoginFragment")
            .build()
        val privacyPolicyNote = HtmlCompat.fromHtml(
            stringResource(
                R.string.login_google_client_privacy_policy,
                context.getString(R.string.app_name),
                privacyPolicyUrl.toString()
            ), 0
        ).toAnnotatedString()
        ClickableTextWithLink(
            privacyPolicyNote,
            style = MaterialTheme.typography.body2
        )

        val limitedUseNote = HtmlCompat.fromHtml(
            stringResource(R.string.login_google_client_limited_use, context.getString(R.string.app_name), GOOGLE_POLICY_URL), 0
        ).toAnnotatedString()
        ClickableTextWithLink(
            limitedUseNote,
            style = MaterialTheme.typography.body2,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

@Composable
@Preview(showBackground = true)
fun PreviewGoogleLogin_withDefaultEmail() {
    GoogleLoginScreen("example@example.example") { _, _ -> }
}

@Composable
@Preview(showBackground = true)
fun PreviewGoogleLogin_empty() {
    GoogleLoginScreen("") { _, _ -> }
}