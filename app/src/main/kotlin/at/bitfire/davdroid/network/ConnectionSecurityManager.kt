/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import androidx.annotation.VisibleForTesting
import at.bitfire.cert4android.CustomCertManager
import java.lang.ref.SoftReference
import java.security.KeyStore
import java.util.Optional
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.jvm.optionals.getOrNull

/**
 * Caching provider for [ConnectionSecurityContext].
 */
@Singleton
class ConnectionSecurityManager @Inject constructor(
    private val customHostnameVerifier: Optional<CustomCertManager.HostnameVerifier>,
    private val customTrustManager: Optional<CustomCertManager>,
    private val keyManagerFactory: ClientCertKeyManager.Factory,
    private val logger: Logger
) {

    /**
     * Maps client certificate aliases (or `null` if no client authentication is used) to their SSLSocketFactory.
     * Uses soft references for the values so that they can be garbage-collected when not used anymore.
     *
     * Not thread-safe, access must be synchronized by caller.
     */
    private val socketFactoryCache: MutableMap<String?, SoftReference<SSLSocketFactory>> =
        LinkedHashMap(2)    // usually not more than: one for no client certificates + one for a certain certificate alias

    /**
     * The default TrustManager to use for connections. If [customTrustManager] provides a value, that value is
     * used. Otherwise, the platform's default trust manager is used.
     */
    private val trustManager by lazy { customTrustManager.getOrNull() ?: defaultTrustManager() }

    /**
     * Provides the [ConnectionSecurityContext] for a given [certificateAlias].
     *
     * Uses [socketFactoryCache] to cache the entries (per [certificateAlias]).
     *
     * @param certificateAlias  alias of the client certificate that shall be used for authentication (`null` for none)
     * @return the connection security context
     */
    fun getContext(certificateAlias: String?): ConnectionSecurityContext {
        /* We only need a custom socket factory for
           - client certificates and/or
           - when cert4android is active (= there's a custom trustManager). */
        val socketFactory = if (certificateAlias != null || customTrustManager.isPresent)
            getSocketFactory(certificateAlias)
        else
            null

        return ConnectionSecurityContext(
            sslSocketFactory = socketFactory,
            trustManager = if (socketFactory != null) trustManager else null,   // when there's a customTrustManager, there's always a socketFactory, too
            hostnameVerifier = customHostnameVerifier.getOrNull(),
            disableHttp2 = certificateAlias != null
        )
    }

    @VisibleForTesting
    internal fun getSocketFactory(certificateAlias: String?): SSLSocketFactory = synchronized(socketFactoryCache) {
        // look up cache first
        val cachedFactory = socketFactoryCache[certificateAlias]?.get()
        if (cachedFactory != null) {
            logger.fine("Using cached SSLSocketFactory (certificateAlias=$certificateAlias)")
            return cachedFactory
        } else
            logger.fine("Creating new SSLSocketFactory (certificateAlias=$certificateAlias)")
        // no cached value, calculate and store into cache

        // when a client certificate alias is given, create and use the respective ClientKeyManager
        val clientKeyManager = certificateAlias?.let { keyManagerFactory.create(it) }

        // create SSLContext that provides the SSLSocketFactory
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(
                /* km = */ if (clientKeyManager != null) arrayOf(clientKeyManager) else null,
                /* tm = */ arrayOf(trustManager),
                /* random = */ null /* default RNG */
            )
        }

        // cache reference and return socket factory
        return sslContext.socketFactory.also { socketFactory ->
            socketFactoryCache[certificateAlias] = SoftReference(socketFactory)
        }
    }

    @VisibleForTesting
    internal fun defaultTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

}