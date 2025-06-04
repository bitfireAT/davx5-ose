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

class GoogleLogin(
    val authService: AuthorizationService
) {
    
    private val logger: Logger = Logger.getGlobal()


    companion object {

        // davx5integration@gmail.com (for davx5-ose)
        private const val CLIENT_ID = "1069050168830-eg09u4tk1cmboobevhm4k3bj1m4fav9i.apps.googleusercontent.com"

        private val SCOPES = arrayOf(
            "https://www.googleapis.com/auth/calendar",     // CalDAV
            "https://www.googleapis.com/auth/carddav"       // CardDAV
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

        private val serviceConfig = AuthorizationServiceConfiguration(
            "https://accounts.google.com/o/oauth2/v2/auth".toUri(),
            "https://oauth2.googleapis.com/token".toUri()
        )

    }

    fun signIn(email: String, customClientId: String?, locale: String?): AuthorizationRequest {
        val builder = AuthorizationRequest.Builder(
            serviceConfig,
            customClientId ?: CLIENT_ID,
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