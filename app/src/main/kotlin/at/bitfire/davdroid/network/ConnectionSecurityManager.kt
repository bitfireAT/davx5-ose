/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import at.bitfire.cert4android.CustomCertManager
import java.security.KeyStore
import java.util.Optional
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.jvm.optionals.getOrNull

/**
 * Caching provider for [SSLContext].
 */
@Singleton
class ConnectionSecurityManager @Inject constructor(
    private val customHostnameVerifier: Optional<CustomCertManager.HostnameVerifier>,
    customTrustManager: Optional<CustomCertManager>,
    private val keyManagerFactory: ClientCertKeyManager.Factory,
    private val logger: Logger,
) {

    // TODO: check HTTP/2

    private val trustManager = customTrustManager.getOrNull() ?: defaultTrustManager()

    fun getContext(certificateAlias: String?): ConnectionSecurityContext {
        val clientKeyManager = certificateAlias?.let { getClientKeyManager(it) }

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(
            /* km = */ if (clientKeyManager != null) arrayOf(clientKeyManager) else null,
            /* tm = */ arrayOf(trustManager),
            /* random = */ null
        )

        return ConnectionSecurityContext(
            sslSocketFactory = sslContext.socketFactory,
            trustManager = trustManager,
            hostnameVerifier = customHostnameVerifier.getOrNull(),
            disableHttp2 = certificateAlias != null
        )
    }

    fun getClientKeyManager(alias: String): KeyManager? =
        try {
            val manager = keyManagerFactory.create(alias)
            logger.fine("Using certificate $alias for authentication")

            manager
        } catch (e: IllegalArgumentException) {
            logger.log(Level.SEVERE, "Couldn't create KeyManager for certificate $alias", e)
            null
        }

    private fun defaultTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

}