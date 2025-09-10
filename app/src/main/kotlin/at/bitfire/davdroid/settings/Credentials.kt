/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

import at.bitfire.davdroid.util.SensitiveString
import net.openid.appauth.AuthState

/**
 * Represents credentials that are used to authenticate against a CalDAV/CardDAV/WebDAV server.
 *
 * Note: [authState] can change from request to request, so make sure that you have an up-to-date
 * copy when using it.
 */
data class Credentials(
    /** username for Basic / Digest auth */
    val username: String? = null,
    /** password for Basic / Digest auth */
    val password: SensitiveString? = null,

    /** alias of an client certificate that is present on the system */
    val certificateAlias: String? = null,

    /** OAuth authorization state */
    val authState: AuthState? = null
) {

    override fun toString(): String {
        val s = mutableListOf<String>()

        if (username != null)
            s += "userName=$username"
        if (password != null)
            s += "password=*****"

        if (certificateAlias != null)
            s += "certificateAlias=$certificateAlias"

        if (authState != null)      // contains sensitive information (refresh token, access token)
            s += "authState=${authState.jsonSerializeString()}"

        return "Credentials(" + s.joinToString(", ") + ")"
    }

}