/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.model

class Credentials(
        val userName: String? = null,
        val password: String? = null,
        val certificateAlias: String? = null
) {

    override fun toString(): String {
        val maskedPassword = "*****".takeIf { password != null }
        return "Credentials(userName=$userName, password=$maskedPassword, certificateAlias=$certificateAlias)"
    }

}