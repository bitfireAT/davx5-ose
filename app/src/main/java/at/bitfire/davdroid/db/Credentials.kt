/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.db

data class Credentials(
        val userName: String? = null,
        val password: String? = null,
        val certificateAlias: String? = null
) {

    override fun toString(): String {
        val maskedPassword = "*****".takeIf { password != null }
        return "Credentials(userName=$userName, password=$maskedPassword, certificateAlias=$certificateAlias)"
    }

}