/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import at.bitfire.cert4android.CustomCertManager
import java.lang.ref.SoftReference
import java.security.KeyStore
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
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
    customTrustManager: Optional<CustomCertManager>,
    private val keyManagerFactory: ClientCertKeyManager.Factory
) {

    /**
     * Maps client certificate aliases (or `null` if no client authentication is used) to their SSLSocketFactory.
     * Uses soft references for the socket factories so that they can be garbage-collected when not used anymore.
     *
     * Not thread-safe, access has to be synchronized by caller.
     */
    private val socketFactoryCache: MutableMap<Optional<String>, SoftReference<SSLSocketFactory>> =
        ConcurrentHashMap(2)    // usually not more than: one for no client certificates + one for a certain certificate alias

    /**
     * The TrustManager that shall be used for all connections. This is either the cert4android custom TrustManager
     * (if available and enabled by build flags), or the default TrustManager.
     */
    private val trustManager: X509TrustManager = customTrustManager.getOrNull() ?: defaultTrustManager()

    /**
     * Provides the [ConnectionSecurityContext] for a given [certificateAlias].
     *
     * Uses [socketFactoryCache] to cache the entries (per [certificateAlias]).
     *
     * @param certificateAlias  alias of the client certificate that shall be used for authentication (`null` for none)
     * @return the connection security context
     */
    fun getContext(certificateAlias: String?): ConnectionSecurityContext {
        val socketFactory = synchronized(socketFactoryCache) {      // synchronize map access
            val certKey = Optional.ofNullable(certificateAlias)
            val cachedFactory = socketFactoryCache[certKey]?.get()
            if (cachedFactory != null)
                return@synchronized cachedFactory
            // no cached value, calculate and store into cache

            // when a client certificate alias is given, create and use the respective ClientKeyManager
            val clientKeyManager = certificateAlias?.let { keyManagerFactory.create(it) }

            // create SSLContext that provides the SSLSocketFactory
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(
                    /* km = */ if (clientKeyManager != null) arrayOf(clientKeyManager) else null,
                    /* tm = */ arrayOf(trustManager),
                    /* random = */ null
                )
            }

            // cache reference and return socket factory
            sslContext.socketFactory.also { factory ->
                socketFactoryCache[certKey] = SoftReference(factory)
            }
        }

        return ConnectionSecurityContext(
            sslSocketFactory = socketFactory,
            trustManager = trustManager,
            hostnameVerifier = customHostnameVerifier.getOrNull(),
            disableHttp2 = certificateAlias != null
        )
    }

    private fun defaultTrustManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

}