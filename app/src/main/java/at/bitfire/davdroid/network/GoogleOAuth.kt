/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import android.net.Uri
import at.bitfire.davdroid.BuildConfig
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

object GoogleOAuth {

    // davx5integration@gmail.com
    private const val CLIENT_ID = "1069050168830-eg09u4tk1cmboobevhm4k3bj1m4fav9i.apps.googleusercontent.com"

    val SCOPES = arrayOf(
        "https://www.googleapis.com/auth/calendar",     // CalDAV
        "https://www.googleapis.com/auth/carddav"       // CardDAV
    )

    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
        Uri.parse("https://oauth2.googleapis.com/token")
    )

    fun authRequestBuilder(clientId: String?) =
        AuthorizationRequest.Builder(
            serviceConfig,
            clientId ?: CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(BuildConfig.APPLICATION_ID + ":/oauth/redirect")
        )

}