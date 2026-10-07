/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.Response
import at.bitfire.dav4jvm.ktor.exception.DavException
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
 * @return the multi-get item, or `null` if this response doesn't contain data (logged as warning)
 *
 * @throws DavException if this response doesn't contain an ETag
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
        WebDavCollection.MultiGetItem(
            url = response.href.omitTrailingSlash(),
            eTag = response.requireETag(),
            scheduleTag = response[ScheduleTag::class.java]?.scheduleTag,
            content = content
        )
    }
}

/**
 * Returns this response's ETag.
 *
 * @throws DavException if this response doesn't contain a [GetETag] property
 */
fun Response.requireETag(): String =
    this[GetETag::class.java]?.eTag
        ?: throw DavException("Server didn't provide ETag for ${this.href}")

/**
 * Extracts the [SyncState] (`sync-token` or `CTag`) reported by this response, if any.
 */
internal fun Response.syncState(): SyncState? =
    this[SyncToken::class.java]?.token?.let {
        SyncState(SyncState.Type.SYNC_TOKEN, it)
    } ?: this[GetCTag::class.java]?.cTag?.let {
        SyncState(SyncState.Type.CTAG, it)
    }
