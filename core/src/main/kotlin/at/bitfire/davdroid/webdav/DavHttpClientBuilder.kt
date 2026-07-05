/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import at.bitfire.davdroid.network.HttpClientBuilder
import com.google.errorprone.annotations.MustBeClosed
import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.LogLevel
import javax.inject.Inject

class DavHttpClientBuilder @Inject constructor(
    private val credentialsStore: CredentialsStore,
    private val httpClientBuilder: HttpClientBuilder,
) {

    /**
     * Creates a Ktor HTTP client that can be used to access resources in the given mount.
     *
     * @param mountId    ID of the mount to access
     * @param logBody    whether to log the body of HTTP requests (disable for potentially large files)
     * @return the new HttpClient which **must be closed by the caller**
     */
    @MustBeClosed
    fun buildKtor(mountId: Long, logBody: Boolean = true): HttpClient {
        val builder = createBuilder(mountId, logBody)
        return builder.buildKtor()
    }

    /**
     * Creates and configures an HttpClientBuilder with authentication and cookie store.
     *
     * @param mountId    ID of the mount to access
     * @param logBody    whether to log the body of HTTP requests (disable for potentially large files)
     * @return configured HttpClientBuilder ready for building
     */
    private fun createBuilder(mountId: Long, logBody: Boolean = true): HttpClientBuilder {
        var builder = httpClientBuilder
            // Ktor's LogLevel.ALL logs headers + body (unlike LogLevel.BODY, which omits headers)
            .loggerInterceptorLevel(if (logBody) LogLevel.ALL else LogLevel.HEADERS)

        credentialsStore.getCredentials(mountId)?.let { credentials ->
            builder = builder.authenticate(
                domain = null,
                getCredentials = { credentials }
            )
        }

        return builder
    }

}