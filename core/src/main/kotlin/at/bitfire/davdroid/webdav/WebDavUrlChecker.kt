/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import at.bitfire.dav4jvm.HttpUtils.toHttpUrl
import at.bitfire.dav4jvm.HttpUtils.toKtorUrl
import at.bitfire.dav4jvm.ktor.DavResource
import at.bitfire.davdroid.network.HttpClientBuilder
import at.bitfire.davdroid.settings.Credentials
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Provider

/**
 * Checks if WebDAV is supported at a given URL.
 */
class WebDavUrlChecker @Inject constructor(
    private val httpClientBuilder: Provider<HttpClientBuilder>
) {
    /**
     * Checks whether WebDAV is supported at given URL with given credentials and returns the resulting URL after
     * following redirects.
     *
     * @param url The URL to check
     * @param credentials The credentials to use for the request
     * @return The URL at which WebDAV support was found
     */
    suspend fun getWebDavUrl(
        url: HttpUrl,
        credentials: Credentials?
    ): HttpUrl? {
        val validVersions = arrayOf("1", "2", "3")

        val builder = httpClientBuilder.get()
        if (credentials != null)
            builder.authenticate(
                domain = null,
                getCredentials = { credentials }
            )

        var webdavUrl: HttpUrl? = null
        builder.buildKtor().use { httpClient ->
            val dav = DavResource(httpClient, url.toKtorUrl())
            dav.options(followRedirects = true) { davCapabilities, _ ->
                if (davCapabilities.any { it in validVersions })
                    webdavUrl = dav.location.toHttpUrl()
            }
        }

        return webdavUrl
    }
}
