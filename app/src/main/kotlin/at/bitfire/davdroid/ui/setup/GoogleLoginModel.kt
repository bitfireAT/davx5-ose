/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.ui.setup

import android.accounts.AccountManager
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.GoogleLogin
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import org.apache.commons.lang3.StringUtils
import java.util.Locale
import java.util.logging.Level
import javax.inject.Inject

@HiltViewModel
class GoogleLoginModel @Inject constructor(
    val context: Application,
    val authService: AuthorizationService
): ViewModel() {

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
        val emailWithDomain = StringUtils.appendIfMissing(email, "@gmail.com")
    }

    var uiState by mutableStateOf(UiState())
        private set

    fun initialize(loginInfo: LoginInfo) {
        uiState = uiState.copy(
            email = loginInfo.credentials?.username ?: findGoogleAccount() ?: "",
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
            customClientId = StringUtils.trimToNull(uiState.customClientId),
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
                Logger.log.log(Level.WARNING, "Google authentication failed", e)
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