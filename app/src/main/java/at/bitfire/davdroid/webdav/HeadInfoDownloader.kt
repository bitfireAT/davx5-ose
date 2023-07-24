/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.webdav

import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.HttpUtils
import at.bitfire.dav4jvm.property.GetETag
import at.bitfire.davdroid.network.HttpClient
import okhttp3.HttpUrl
import java.time.Instant
import java.util.*
import java.util.concurrent.Callable

class HeadInfoDownloader(
    val client: HttpClient,
    val url: HttpUrl
): Callable<HeadResponse> {

    override fun call(): HeadResponse {
        var size: Long? = null
        var eTag: String? = null
        var lastModified: Instant? = null
        var supportsPartial: Boolean? = null

        DavResource(client.okHttpClient, url).head { response ->
            response.header("ETag", null)?.let {
                val getETag = GetETag(it)
                if (!getETag.weak)
                    eTag = getETag.eTag
            }
            response.header("Last-Modified", null)?.let {
                lastModified = HttpUtils.parseDate(it)?.toInstant()
            }
            response.headers["Content-Length"]?.let {
                size = it.toLong()
            }
            response.headers["Accept-Ranges"]?.let { acceptRangesStr ->
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