/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import net.openid.appauth.AuthState

data class Credentials(
    val username: String? = null,
    val password: CharArray? = null,

    val certificateAlias: String? = null,

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

        if (authState != null)
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