/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.model

import java.io.Serializable

class Credentials @JvmOverloads constructor(
        @JvmField val userName: String? = null,
        @JvmField val password: String? = null,
        @JvmField val certificateAlias: String? = null
): Serializable {

    enum class Type {
        UsernamePassword,
        ClientCertificate
    }

    val type: Type

    init {
        type = when {
            !certificateAlias.isNullOrEmpty() ->
                Type.ClientCertificate
            !userName.isNullOrEmpty() && !password.isNullOrEmpty() ->
                Type.UsernamePassword
            else ->
                throw IllegalArgumentException("Either username/password or certificate alias must be set")
        }
    }

    override fun toString(): String {
        return "Credentials(type=$type, userName=$userName, certificateAlias=$certificateAlias)"
    }

}