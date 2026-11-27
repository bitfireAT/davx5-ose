/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import androidx.annotation.VisibleForTesting
import at.bitfire.dav4jvm.ktor.DavResource
import at.bitfire.dav4jvm.ktor.parent
import at.bitfire.dav4jvm.ktor.selfResponse
import at.bitfire.dav4jvm.ktor.withTrailingSlash
import at.bitfire.dav4jvm.property.webdav.ResourceType
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.network.HttpClientBuilder
import at.bitfire.davdroid.settings.Credentials
import io.ktor.client.HttpClient
import io.ktor.http.Url
import javax.inject.Inject

/**
 * Checks if WebDAV is supported at a given URL.
 */
class WebDavUrlChecker @Inject constructor(
    private val httpClientBuilder: HttpClientBuilder
) {

    /**
     * Checks whether given URL with given credentials is a WebDAV folder that can be mounted.
     *
     * @param url The URL to check
     * @param credentials The credentials to use for the request
     * @return The folder URL to mount (after following redirects, with trailing slash), or `null` if there's no WebDAV folder
     */
    suspend fun getWebDavUrl(
        url: Url,
        credentials: Credentials?
    ): Url? {
        var builder = httpClientBuilder
        if (credentials != null)
            builder = builder.authenticate(
                domain = null,
                getCredentials = { credentials }
            )
        return builder.build().use { checkWebDavUrl(it, url) }
    }

    /**
     * Performs the actual check (OPTIONS for the DAV class, PROPFIND for the resource type) using a pre-built [httpClient].
     * Separated from [getWebDavUrl] to allow tests to inject a `MockEngine`.
     * A file URL is checked as its parent folder.
     *
     * @param httpClient The HTTP client to use for the requests
     * @param url The URL to check
     * @return see [getWebDavUrl]
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun checkWebDavUrl(httpClient: HttpClient, url: Url): Url? =
        checkWebDavFolder(httpClient, url, checkParent = true)

    /**
     * Checks whether a URL is a WebDAV folder.
     *
     * @param httpClient The HTTP client to use for the requests
     * @param url The URL to check
     * @param checkParent Whether to check the parent folder instead if [url] isn't a folder
     * @return see [getWebDavUrl]
     */
    private suspend fun checkWebDavFolder(httpClient: HttpClient, url: Url, checkParent: Boolean): Url? {
        val validVersions = arrayOf("1", "2", "3")
        val dav = DavResource(httpClient, url)
        val davCapabilities = dav.options(followRedirects = true).davCapabilities
        if (davCapabilities.none { it in validVersions })
            return null

        // only folders can be mounted
        val types = dav.propfind(0, WebDAV.ResourceType).selfResponse()?.get(ResourceType::class.java)?.types.orEmpty()
        return when {
            WebDAV.Collection in types -> dav.location.withTrailingSlash()
            checkParent -> checkWebDavFolder(httpClient, dav.location.parent(), checkParent = false)
            else -> null
        }
    }

}
