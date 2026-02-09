/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.net.toUri
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.TokenResponse
import javax.inject.Inject

/**
 * Integration with OpenID AppAuth (Android)
 */
@Reusable
class OAuthIntegration @Inject constructor(
    @ApplicationContext context: Context
) {

    /** redirect URI, must be registered in Manifest */
    val redirectUri = (context.packageName + ":/oauth2/redirect").toUri()

    /**
     * Called by the authorization service when the login is finished and [redirectUri] is launched.
     *
     * @param authService   authorization service
     * @param authResponse  response from the server (coming over the Intent from the browser / [AuthorizationContract])
     */
    suspend fun authenticate(authService: AuthorizationService, authResponse: AuthorizationResponse): AuthState {
        val authState = AuthState(authResponse, null)       // authorization code must not be stored; exchange it to refresh token
        val authStateFuture = CompletableDeferred<AuthState>()

        authService.performTokenRequest(authResponse.createTokenExchangeRequest()) { tokenResponse: TokenResponse?, refreshTokenException: AuthorizationException? ->
            if (tokenResponse != null) {
                // success, save authState (= refresh token)
                authState.update(tokenResponse, refreshTokenException)
                authStateFuture.complete(authState)
            } else if (refreshTokenException != null)
                authStateFuture.completeExceptionally(refreshTokenException)
        }

        return authStateFuture.await()
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