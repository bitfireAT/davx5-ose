/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import androidx.core.net.toUri
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import java.net.URI

object OAuthFastmail {

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
    val baseUri: URI = URI.create("https://fastmail.com/")

    private val serviceConfig = AuthorizationServiceConfiguration(
        "https://api.fastmail.com/oauth/authorize".toUri(),
        "https://api.fastmail.com/oauth/refresh".toUri()
    )


    fun signIn(email: String, locale: String?): AuthorizationRequest {
        val builder = AuthorizationRequest.Builder(
            serviceConfig,
            CLIENT_ID,
            ResponseTypeValues.CODE,
            OAuthIntegration.redirectUri
        )
        return builder
            .setScopes(*SCOPES)
            .setLoginHint(email)
            .setUiLocales(locale)
            .build()
    }

}