/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import org.conscrypt.Conscrypt
import java.security.Security
import java.util.logging.Logger
import javax.net.ssl.SSLContext

object ConscryptIntegration {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    private var initialized = false

    @Synchronized
    fun initialize() {
        if (initialized)
            return

        val alreadyInstalled = Security.getProviders().any { Conscrypt.isConscrypt(it) }
        if (!alreadyInstalled) {
            // install Conscrypt as most preferred provider
            Security.insertProviderAt(Conscrypt.newProvider(), 1)

            val version = Conscrypt.version()
            logger.info("Using Conscrypt/${version.major()}.${version.minor()}.${version.patch()} for TLS")

            val engine = SSLContext.getDefault().createSSLEngine()
            logger.info("Enabled protocols: ${engine.enabledProtocols.joinToString(", ")}")
            logger.info("Enabled ciphers: ${engine.enabledCipherSuites.joinToString(", ")}")
        }

        initialized = true
    }

}