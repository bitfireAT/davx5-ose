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

    override fun toString() =
            "Credentials(type=$type, userName=$userName, certificateAlias=$certificateAlias)"

}