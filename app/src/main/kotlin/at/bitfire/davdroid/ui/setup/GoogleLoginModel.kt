/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.setup

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.R
import at.bitfire.davdroid.network.GoogleLogin
import at.bitfire.davdroid.util.trimToNull
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import java.util.Locale
import java.util.logging.Level
import java.util.logging.Logger

@HiltViewModel(assistedFactory = GoogleLoginModel.Factory::class)
class GoogleLoginModel @AssistedInject constructor(
    @Assisted val initialLoginInfo: LoginInfo,
    private val authService: AuthorizationService,
    @ApplicationContext val context: Context,
    private val logger: Logger
): ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(loginInfo: LoginInfo): GoogleLoginModel
    }

    val googleLogin = GoogleLogin(authService)

    override fun onCleared() {
        authService.dispose()
    }


    data class UiState(
        val email: String = "",
        val customClientId: String = "",
        val error: String? = null,

        /** login info (set after successful login) */
        val result: LoginInfo? = null
    ) {
        val canContinue = email.isNotEmpty()
        val emailWithDomain = if (email.contains("@")) email else "$email@gmail.com"
    }

    var uiState by mutableStateOf(UiState())
        private set

    init {
        uiState = uiState.copy(
            email = initialLoginInfo.credentials?.username ?: findGoogleAccount() ?: "",
            error = null,
            result = null
        )
    }

    fun setEmail(email: String) {
        uiState = uiState.copy(email = email)
    }

    fun setCustomClientId(clientId: String) {
        uiState = uiState.copy(customClientId = clientId)
    }

    fun signIn() =
        googleLogin.signIn(
            email = uiState.emailWithDomain,
            customClientId = uiState.customClientId.trimToNull(),
            locale = Locale.getDefault().toLanguageTag()
        )

    fun signInFailed() {
        uiState = uiState.copy(error = context.getString(R.string.install_browser))
    }

    fun authenticate(authResponse: AuthorizationResponse) {
        viewModelScope.launch {
            try {
                val credentials = googleLogin.authenticate(authResponse)

                // success, provide login info to continue
                uiState = uiState.copy(
                    result = LoginInfo(
                        baseUri = GoogleLogin.googleBaseUri(uiState.emailWithDomain),
                        credentials = credentials,
                        suggestedAccountName = uiState.emailWithDomain
                    )
                )
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Google authentication failed", e)
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

    private fun findGoogleAccount(): String? {
        val accountManager = AccountManager.get(context)
        return accountManager
            .getAccountsByType("com.google")
            .map { it.name }
            .firstOrNull()
    }


    inner class AuthorizationContract() : ActivityResultContract<AuthorizationRequest, AuthorizationResponse?>() {
        override fun createIntent(context: Context, input: AuthorizationRequest) =
            authService.getAuthorizationRequestIntent(input)

        override fun parseResult(resultCode: Int, intent: Intent?): AuthorizationResponse? =
            intent?.let { AuthorizationResponse.fromIntent(it) }
    }

}