/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings

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
    val password: CharArray? = null,

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


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Credentials

        if (username != other.username) return false
        if (!password.contentEquals(other.password)) return false
        if (certificateAlias != other.certificateAlias) return false
        if (authState != other.authState) return false

        return true
    }

    override fun hashCode(): Int {
        var result = username?.hashCode() ?: 0
        result = 31 * result + (password?.contentHashCode() ?: 0)
        result = 31 * result + (certificateAlias?.hashCode() ?: 0)
        result = 31 * result + (authState?.hashCode() ?: 0)
        return result
    }

}