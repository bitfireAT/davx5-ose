/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.db

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import net.openid.appauth.AuthState

@Parcelize
data class Credentials(
    val username: String? = null,
    val password: String? = null,

    val certificateAlias: String? = null,

    val authState: @RawValue AuthState? = null
): Parcelable {

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

}