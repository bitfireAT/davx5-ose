/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import at.bitfire.davdroid.network.HttpClient
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Inject
import javax.inject.Provider

class DavHttpClientBuilder @Inject constructor(
    private val cookieStoreManager: CookieStoreManager,
    private val credentialsStore: CredentialsStore,
    private val httpClientBuilder: Provider<HttpClient.Builder>,
) {

    /**
     * Creates an HTTP client that can be used to access resources in the given mount.
     *
     * @param mountId    ID of the mount to access
     * @param logBody    whether to log the body of HTTP requests (disable for potentially large files)
     */
    fun build(mountId: Long, logBody: Boolean = true): HttpClient {
        val builder = httpClientBuilder.get()
            .loggerInterceptorLevel(if (logBody) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.HEADERS)
            .setCookieStore(cookieStoreManager.forMount(mountId))

        credentialsStore.getCredentials(mountId)?.let { credentials ->
            builder.authenticate(host = null, getCredentials = { credentials })
        }

        return builder.build()
    }

}