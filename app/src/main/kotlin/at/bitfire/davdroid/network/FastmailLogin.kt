/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import androidx.core.net.toUri
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.db.Credentials
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenResponse
import java.net.URI
import java.util.logging.Logger

class FastmailLogin(
    val authService: AuthorizationService
) {

    private val logger: Logger = Logger.getGlobal()


    companion object {

        // DAVx5 Client ID (issued by Fastmail)
        private const val CLIENT_ID = "34ce41ae"

        private val SCOPES = arrayOf(
            "https://www.fastmail.com/dev/protocol-caldav", // CalDAV
            "https://www.fastmail.com/dev/protocol-carddav" // CardDAV
        )

        /**
         * The base URL for Fastmail. Note that this URL is used for both CalDAV and CardDAV;
         * the SRV records of the domain are checked to determine the respective service base URL.
         */
        val fastmailBaseUri: URI = URI.create("https://fastmail.com/")

        private val serviceConfig = AuthorizationServiceConfiguration(
            "https://api.fastmail.com/oauth/authorize".toUri(),
            "https://api.fastmail.com/oauth/refresh".toUri()
        )

    }

    fun signIn(email: String, locale: String?): AuthorizationRequest {
        val builder = AuthorizationRequest.Builder(
            serviceConfig,
            CLIENT_ID,
            ResponseTypeValues.CODE,
            (BuildConfig.APPLICATION_ID + ":/oauth2/redirect").toUri()
        )
        return builder
            .setScopes(*SCOPES)
            .setLoginHint(email)
            .setUiLocales(locale)
            .build()
    }

    suspend fun authenticate(authResponse: AuthorizationResponse): Credentials {
        val authState = AuthState(authResponse, null)       // authorization code must not be stored; exchange it to refresh token
        val credentials = CompletableDeferred<Credentials>()

        withContext(Dispatchers.IO) {
            authService.performTokenRequest(authResponse.createTokenExchangeRequest()) { tokenResponse: TokenResponse?, refreshTokenException: AuthorizationException? ->
                logger.info("Refresh token response: ${tokenResponse?.jsonSerializeString()}")

                if (tokenResponse != null) {
                    // success, save authState (= refresh token)
                    authState.update(tokenResponse, refreshTokenException)
                    credentials.complete(Credentials(authState = authState))
                }
            }
        }

        return credentials.await()
    }

}