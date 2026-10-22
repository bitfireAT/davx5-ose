/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package com.davx5.feature.preconfiguredsettings.shared

import at.bitfire.davdroid.settings.Credentials
import at.bitfire.davdroid.ui.setup.LoginInfo
import at.bitfire.davdroid.util.DavUtils.toURIorNull
import at.bitfire.synctools.util.SensitiveString.Companion.toSensitiveString
import kotlinx.serialization.Serializable

/**
 * Represents preconfigured settings for establishing a connection to a CalDAV/CardDAV server.
 *
 * This class holds the necessary configuration details such as the base root
 * URI and basic authentication credentials.
 */
@Serializable
data class PreconfiguredSettings(
    val baseRoot: String? = null,
    val basicAuth: BasicAuthCredentials? = null,
) {
    /**
     * Obtains an instance of [LoginInfo] from the data of the preconfigured settings.
     * @throws IllegalStateException if [baseRoot] is `null` or invalid.
     */
    fun toLoginInfo(preconfigurationUrl: String): LoginInfo {
        checkNotNull(baseRoot) { "baseRoot cannot be null." }
        val baseRootURI = baseRoot.toURIorNull()
        checkNotNull(baseRootURI) { "baseRoot is not a valid URI." }

        return LoginInfo(
            baseUri = baseRootURI,
            credentials = basicAuth?.toCredentials(),
            suggestedAccountName = basicAuth?.username,
            preconfigurationUrl = preconfigurationUrl
        )
    }

    @Serializable
    data class BasicAuthCredentials(val username: String, private val password: String? = null) {
        fun toCredentials(): Credentials = Credentials(username, password?.toSensitiveString())

        override fun toString(): String {
            return "BasicAuthCredentials[username=$username, password=${password?.let { "***" } ?: "null"}]"
        }
    }
}
