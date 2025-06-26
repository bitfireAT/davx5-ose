/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.net.toUri
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.network.OAuthIntegration.redirectUri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.TokenResponse
import java.util.Locale

/**
 * Integration with OpenID AppAuth (Android)
 */
object OAuthIntegration {

    /** redirect URI, must be registered in Manifest */
    val redirectUri =
        (BuildConfig.APPLICATION_ID + ":/oauth2/redirect").toUri()

    /**
     * Called by the authorization service when the login is finished and [redirectUri] is launched.
     *
     * @param authService   authorization service
     * @param authResponse  response from the server (coming over the Intent from the browser / [AuthorizationContract])
     */
    suspend fun authenticate(authService: AuthorizationService, authResponse: AuthorizationResponse): Credentials {
        val authState = AuthState(authResponse, null)       // authorization code must not be stored; exchange it to refresh token
        val credentials = CompletableDeferred<Credentials>()

        withContext(Dispatchers.IO) {
            authService.performTokenRequest(authResponse.createTokenExchangeRequest()) { tokenResponse: TokenResponse?, refreshTokenException: AuthorizationException? ->
                if (tokenResponse != null) {
                    // success, save authState (= refresh token)
                    authState.update(tokenResponse, refreshTokenException)
                    credentials.complete(Credentials(authState = authState))
                } else if (refreshTokenException != null)
                    credentials.completeExceptionally(refreshTokenException)
            }
        }

        return credentials.await()
    }

    /**
     * Creates a new authorization request from a known configuration. Typically used to re-authorize
     * from a given configuration.
     *
     * @param authState    current authorization state that shall be replaced
     * @return authorization request, or `null` if the current config doesn't contain a known provider
     */
    fun newAuthorizeRequest(authState: AuthState): AuthorizationRequest? {
        val authConfig = authState.authorizationServiceConfiguration ?: return null
        val authHost = authConfig.authorizationEndpoint.host.toString()
        val locale = Locale.getDefault().toLanguageTag()

        // If more OAuth providers become added, this should be rewritten so that all providers
        // are checked automatically.
        return when {
            authHost.contains("fastmail.com") ->
                OAuthFastmail.signIn(null, locale)

            authHost.contains("google.com") ->
                OAuthGoogle.signIn(null, authState.lastAuthorizationResponse?.request?.clientId, locale)

            else -> return null
        }
    }


    class AuthorizationContract(
        private val authService: AuthorizationService
    ) : ActivityResultContract<AuthorizationRequest, AuthorizationResponse?>() {
        override fun createIntent(context: Context, input: AuthorizationRequest) =
            authService.getAuthorizationRequestIntent(input)

        override fun parseResult(resultCode: Int, intent: Intent?): AuthorizationResponse? =
            intent?.let { AuthorizationResponse.fromIntent(it) }
    }

}