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
import at.bitfire.davdroid.R
import at.bitfire.davdroid.network.OAuthFastmail
import at.bitfire.davdroid.network.OAuthIntegration
import at.bitfire.davdroid.settings.Credentials
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import java.util.Locale
import java.util.logging.Level
import java.util.logging.Logger

@HiltViewModel(assistedFactory = FastmailLoginModel.Factory::class)
class FastmailLoginModel @AssistedInject constructor(
    @Assisted val initialLoginInfo: LoginInfo,
    private val authService: AuthorizationService,
    @ApplicationContext val context: Context,
    private val logger: Logger,
    private val oAuthFastmail: OAuthFastmail,
    private val oAuthIntegration: OAuthIntegration
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(loginInfo: LoginInfo): FastmailLoginModel
    }

    override fun onCleared() {
        authService.dispose()
    }


    data class UiState(
        val email: String = "",
        val error: String? = null,

        /** login info (set after successful login) */
        val result: LoginInfo? = null
    ) {
        val canContinue = email.isNotEmpty()
        val emailWithDomain = if (email.contains("@")) email else "$email@fastmail.com"
    }

    var uiState by mutableStateOf(UiState())
        private set

    init {
        uiState = uiState.copy(
            email = initialLoginInfo.credentials?.username ?: "",
            error = null,
            result = null
        )
    }

    fun setEmail(email: String) {
        uiState = uiState.copy(email = email)
    }


    fun authorizationContract() = OAuthIntegration.AuthorizationContract(authService)

    fun signIn() =
        oAuthFastmail.signIn(
            email = uiState.emailWithDomain,
            locale = Locale.getDefault().toLanguageTag()
        )

    fun signInFailed() {
        uiState = uiState.copy(error = context.getString(R.string.install_browser))
    }

    fun authenticate(authResponse: AuthorizationResponse) {
        viewModelScope.launch {
            try {
                val credentials = Credentials(authState = oAuthIntegration.authenticate(authService, authResponse))

                // success, provide login info to continue
                uiState = uiState.copy(
                    result = LoginInfo(
                        baseUri = oAuthFastmail.baseUri,
                        credentials = credentials,
                        suggestedAccountName = uiState.emailWithDomain
                    )
                )
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Fastmail authentication failed", e)
                uiState = uiState.copy(error = e.message)
            }
        }
    }

    fun authCodeFailed() {
        uiState = uiState.copy(error = context.getString(R.string.login_oauth_couldnt_obtain_auth_code))
    }

    fun resetResult() {
        uiState = uiState.copy(result = null)
    }

}