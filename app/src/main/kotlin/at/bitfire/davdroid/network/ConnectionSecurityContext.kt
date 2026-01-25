/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.network

import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

class ConnectionSecurityContext(
    val sslSocketFactory: SSLSocketFactory?,
    val trustManager: X509TrustManager?,
    val hostnameVerifier: HostnameVerifier?,
    val disableHttp2: Boolean
)