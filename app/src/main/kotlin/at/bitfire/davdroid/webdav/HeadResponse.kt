/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.webdav

import at.bitfire.dav4jvm.HttpUtils
import at.bitfire.dav4jvm.ktor.DavResource
import at.bitfire.dav4jvm.property.webdav.GetETag
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import java.time.Instant

/**
 * Represents the information that was retrieved via a HEAD request before
 * accessing the file.
 */
data class HeadResponse(
    val size: Long? = null,
    val eTag: String? = null,
    val lastModified: Instant? = null,

    val supportsPartial: Boolean? = null
) {

    companion object {

        suspend fun fromUrl(client: HttpClient, url: Url): HeadResponse {
            var size: Long? = null
            var eTag: String? = null
            var lastModified: Instant? = null
            var supportsPartial: Boolean? = null

            DavResource(client, url).head { response ->
                response.headers[HttpHeaders.ETag]?.let {
                    val getETag = GetETag(it)
                    if (!getETag.weak)
                        eTag = getETag.eTag
                }
                response.headers[HttpHeaders.LastModified]?.let {
                    lastModified = HttpUtils.parseDate(it)
                }
                response.headers[HttpHeaders.ContentLength]?.let {
                    size = it.toLong()
                }
                response.headers[HttpHeaders.AcceptRanges]?.let { acceptRangesStr ->
                    val acceptRanges = acceptRangesStr.split(',').map { it.trim().lowercase() }
                    when {
                        acceptRanges.contains("none") -> supportsPartial = false
                        acceptRanges.contains("bytes") -> supportsPartial = true
                    }
                }
            }
            return HeadResponse(size, eTag, lastModified, supportsPartial)
        }

    }

    fun toDocumentState(): DocumentState? =
        if (eTag != null || lastModified != null)
            DocumentState(eTag, lastModified)
        else
            null

}