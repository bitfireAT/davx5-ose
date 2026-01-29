/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Holds information that shall be used to create TLS connections.
 *
 * @param sslSocketFactory  the socket factory that shall be used
 * @param trustManager      the trust manager that shall be used
 * @param hostnameVerifier  the hostname verifier that shall be used
 * @param disableHttp2      whether HTTP/2 shall be disabled
 */
class ConnectionSecurityContext(
    val sslSocketFactory: SSLSocketFactory?,
    val trustManager: X509TrustManager?,
    val hostnameVerifier: HostnameVerifier?,
    val disableHttp2: Boolean
)