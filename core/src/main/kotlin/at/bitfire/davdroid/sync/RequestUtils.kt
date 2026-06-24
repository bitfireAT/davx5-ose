/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import at.bitfire.dav4jvm.QuotedStringUtils
import at.bitfire.dav4jvm.ktor.DavResource
import at.bitfire.dav4jvm.ktor.ResponseCallback
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headers
import io.ktor.util.appendAll
import java.io.StringWriter

suspend fun DavResource.put(
    body: OutgoingContent,
    ifETag: String? = null,
    ifScheduleTag: String? = null,
    ifNoneMatch: Boolean = false,
    headers: Map<String, String> = emptyMap(),
    callback: ResponseCallback
) {
    put(
        body,
        additionalHeaders = headers {
            if (ifETag != null)
                // only overwrite specific version
                append(HttpHeaders.IfMatch, QuotedStringUtils.asQuotedString(ifETag))
            if (ifScheduleTag != null)
                // only overwrite specific version
                append(HttpHeaders.IfScheduleTagMatch, QuotedStringUtils.asQuotedString(ifScheduleTag))
            if (ifNoneMatch)
                // don't overwrite anything existing
                append(HttpHeaders.IfNoneMatch, "*")

            // Append all custom headers
            appendAll(headers)
        },
        callback = callback
    )
}

suspend fun DavResource.delete(
    ifETag: String? = null,
    ifScheduleTag: String? = null,
    headers: Map<String, String> = emptyMap(),
    callback: ResponseCallback
) {
    delete(
        additionalHeaders = headers {
            if (ifETag != null)
                append(HttpHeaders.IfMatch, QuotedStringUtils.asQuotedString(ifETag))
            if (ifScheduleTag != null)
                append(HttpHeaders.IfScheduleTagMatch, QuotedStringUtils.asQuotedString(ifScheduleTag))

            // Append all custom headers
            appendAll(headers)
        },
        callback = callback
    )
}

class ByteArrayContentImpl(
    private val bytes: ByteArray,
    override val contentType: ContentType? = null
): OutgoingContent.ByteArrayContent() {
    override fun bytes(): ByteArray = bytes
}

fun StringWriter.toOutgoingContent(
    contentType: ContentType? = null
): OutgoingContent = ByteArrayContentImpl(
    bytes = toString().encodeToByteArray(),
    contentType = contentType
)
