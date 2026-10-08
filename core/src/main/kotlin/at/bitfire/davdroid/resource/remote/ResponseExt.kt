/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.Response
import at.bitfire.dav4jvm.ktor.omitTrailingSlash
import at.bitfire.dav4jvm.property.caldav.GetCTag
import at.bitfire.dav4jvm.property.caldav.ScheduleTag
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.SyncToken
import at.bitfire.davdroid.resource.local.SyncState
import at.bitfire.davdroid.sync.withExceptionContext
import java.util.logging.Logger

private val logger = Logger.getLogger("at.bitfire.davdroid.resource.remote.ResponseExt")

/**
 * Turns this multi-get response into a [WebDavCollection.MultiGetItem].
 *
 * @param getContent extracts the data (calendar-data or address-data) from this response
 *
 * @return the multi-get item, or `null` if this response doesn't contain data or ETag (logged as warning)
 *
 * @throws IllegalArgumentException if this response is not successful (callers must filter beforehand)
 */
suspend fun Response.asMultiGetItem(getContent: (Response) -> String?): WebDavCollection.MultiGetItem? {
    val response = this
    require(response.isSuccess()) { "Must only be called for successful responses" }

    return response.href.withExceptionContext {
        /* Some servers (e.g. Posteo, see #1700, #2941) send 200 responses without data. Skip them instead
        of throwing, so that a single broken resource doesn't abort the sync of the whole collection. */
        val content = getContent(response)
        if (content == null) {
            logger.warning("Ignoring multi-get response without data: ${response.href}")
            return@withExceptionContext null
        }
        // ETag is required by RFC 4791 5.3.4 / RFC 6352 6.3.2; without it, we couldn't detect changes later (#2934)
        val eTag = response[GetETag::class.java]?.eTag
        if (eTag == null) {
            logger.warning("Ignoring multi-get response without ETag: ${response.href}")
            return@withExceptionContext null
        }
        WebDavCollection.MultiGetItem(
            url = response.href.omitTrailingSlash(),
            eTag = eTag,
            scheduleTag = response[ScheduleTag::class.java]?.scheduleTag,
            content = content
        )
    }
}

/**
 * Extracts the [SyncState] (`sync-token` or `CTag`) reported by this response, if any.
 */
internal fun Response.syncState(): SyncState? =
    this[SyncToken::class.java]?.token?.let {
        SyncState(SyncState.Type.SYNC_TOKEN, it)
    } ?: this[GetCTag::class.java]?.cTag?.let {
        SyncState(SyncState.Type.CTAG, it)
    }
