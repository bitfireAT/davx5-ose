/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import at.bitfire.dav4jvm.ktor.DavResource
import at.bitfire.davdroid.network.HttpClientBuilder
import at.bitfire.davdroid.settings.Credentials
import io.ktor.http.Url
import javax.inject.Inject

/**
 * Checks if WebDAV is supported at a given URL.
 */
class WebDavUrlChecker @Inject constructor(
    private val httpClientBuilder: HttpClientBuilder
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
        url: Url,
        credentials: Credentials?
    ): Url? {
        val validVersions = arrayOf("1", "2", "3")

        var builder = httpClientBuilder
        if (credentials != null)
            builder = builder.authenticate(
                domain = null,
                getCredentials = { credentials }
            )

        var webdavUrl: Url? = null
        builder.buildKtor().use { httpClient ->
            val dav = DavResource(httpClient, url)
            dav.options(followRedirects = true) { davCapabilities, _ ->
                if (davCapabilities.any { it in validVersions })
                    webdavUrl = dav.location
            }
        }

        return webdavUrl
    }
}
