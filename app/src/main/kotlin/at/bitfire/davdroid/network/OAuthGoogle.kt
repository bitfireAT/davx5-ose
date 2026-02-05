/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import androidx.core.net.toUri
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import java.net.URI
import javax.inject.Inject

class OAuthGoogle @Inject constructor(
    private val oAuthIntegration: OAuthIntegration
) {

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
    fun baseUri(googleAccount: String): URI =
        URI("https", "apidata.googleusercontent.com", "/caldav/v2/$googleAccount/user", null)

    private val serviceConfig = AuthorizationServiceConfiguration(
        "https://accounts.google.com/o/oauth2/v2/auth".toUri(),
        "https://oauth2.googleapis.com/token".toUri()
    )


    fun signIn(email: String?, customClientId: String?, locale: String?): AuthorizationRequest {
        val builder = AuthorizationRequest.Builder(
            serviceConfig,
            customClientId ?: CLIENT_ID,
            ResponseTypeValues.CODE,
            oAuthIntegration.redirectUri
        )
        return builder
            .setScopes(*SCOPES)
            .setLoginHint(email)
            .setUiLocales(locale)
            .build()
    }


    companion object {

        // davx5integration@gmail.com (for davx5-ose)
        private const val CLIENT_ID = "1069050168830-eg09u4tk1cmboobevhm4k3bj1m4fav9i.apps.googleusercontent.com"

    }

}