/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import androidx.core.net.toUri
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.db.Credentials
import at.bitfire.davdroid.network.OAuthIntegration.redirectUri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.TokenResponse

/**
 * Integration with OpenID AppAuth (Android)
 */
object OAuthIntegration {

    /** redirect URI, must be registered in Manifest */
    val redirectUri =
        (BuildConfig.APPLICATION_ID + ":/oauth2/redirect").toUri()

    /**
     * Called by the authorization service when the login is finished and [redirectUri] is launched.
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

}