/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.setup

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.dav4jvm.ktor.toUrlOrNull
import at.bitfire.davdroid.R
import at.bitfire.davdroid.network.OAuthIntegration
import at.bitfire.davdroid.network.OAuthOidc
import at.bitfire.davdroid.settings.Credentials
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.toURI
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import java.util.Locale
import java.util.logging.Level
import java.util.logging.Logger

@HiltViewModel(assistedFactory = OidcLoginViewModel.Factory::class)
class OidcLoginViewModel @AssistedInject constructor(
    @Assisted val initialLoginInfo: LoginInfo,
    private val authService: AuthorizationService,
    @ApplicationContext val context: Context,
    private val logger: Logger,
    private val oAuthOidc: OAuthOidc,
    private val oAuthIntegration: OAuthIntegration
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(loginInfo: LoginInfo): OidcLoginViewModel
    }

    override fun onCleared() {
        authService.dispose()
    }

    data class UiState(
        val url: String = "",
        val clientId: String = "",
        val scope: String = "openid profile email",

        val inProgress: Boolean = false,

        val error: String? = null,

        val authorizationRequest: AuthorizationRequest? = null,
        /** login info (set after successful login) */
        val result: LoginInfo? = null
    ) {
        val urlWithPrefix =
            if (url.startsWith("http://") || url.startsWith("https://"))
                url
            else
                "https://$url"
        val ktorUrl = urlWithPrefix.toUrlOrNull()

        val canContinue = !inProgress && ktorUrl != null && clientId.isNotEmpty()
    }

    var uiState by mutableStateOf(UiState())
        private set

    init {
        uiState = UiState(
            url = initialLoginInfo.baseUri?.toString()?.removePrefix("https://") ?: "",
            clientId = initialLoginInfo.credentials?.username ?: "",
        )
        initialLoginInfo.credentials?.authState?.scope?.let {
            uiState = uiState.copy(scope = it)
        }
    }

    fun setUrl(url: String) {
        uiState = uiState.copy(url = url)
    }

    fun setClientId(clientId: String) {
        uiState = uiState.copy(clientId = clientId)
    }

    fun setScope(scope: String) {
        uiState = uiState.copy(scope = scope)
    }


    fun authorizationContract() = OAuthIntegration.AuthorizationContract(authService)

    fun signInFailed() {
        uiState = uiState.copy(error = context.getString(R.string.install_browser))
    }

    fun signIn() {
        val url = uiState.ktorUrl
        if (uiState.inProgress || url == null)
            return

        uiState = uiState.copy(
            inProgress = true,
            error = null
        )

        viewModelScope.launch {
            try {
                oAuthOidc.fetchServiceConfig(url)

                val request = oAuthOidc.signIn(
                    clientId = uiState.clientId,
                    scope = uiState.scope,
                    locale = Locale.getDefault().toLanguageTag()
                )

                uiState = uiState.copy(
                    inProgress = false,
                    authorizationRequest = request
                )
            } catch (e: Exception) {
                logger.log(Level.WARNING, "OIDC authentication failed", e)

                uiState = uiState.copy(
                    inProgress = false,
                    error = e.message
                )
            }
        }
    }

    fun authenticate(authResponse: AuthorizationResponse) {
        val url = uiState.ktorUrl
        if (uiState.inProgress || url == null)
            return

        uiState = uiState.copy(
            inProgress = true,
            error = null
        )

        viewModelScope.launch {
            try {
                val credentials = Credentials(authState = oAuthIntegration.authenticate(authService, authResponse))

                // success, provide login info to continue
                uiState = uiState.copy(
                    inProgress = false,
                    result = LoginInfo(
                        baseUri = url.toURI(),
                        credentials = credentials,
                        // TODO: this could be queried from the ID Token but requires a JWT parser
                        suggestedAccountName = null
                    )
                )
            } catch (e: Exception) {
                logger.log(Level.WARNING, "OIDC authentication failed", e)

                uiState = uiState.copy(
                    inProgress = false,
                    error = e.message
                )
            }
        }
    }

    fun authCodeFailed() {
        uiState = uiState.copy(error = context.getString(R.string.login_oauth_couldnt_obtain_auth_code))
    }

    fun resetResult() {
        uiState = uiState.copy(
            authorizationRequest = null,
            result = null
        )
    }

}