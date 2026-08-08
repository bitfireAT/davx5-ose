/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.Response
import at.bitfire.dav4jvm.ktor.exception.DavException
import at.bitfire.dav4jvm.property.caldav.GetCTag
import at.bitfire.dav4jvm.property.caldav.ScheduleTag
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.SyncToken
import at.bitfire.davdroid.resource.SyncState
import at.bitfire.davdroid.sync.withExceptionContext

/**
 * Turns this multi-get response into a [WebDavCollection.MultiGetItem].
 *
 * @param getContent extracts the data (calendar-data or address-data) from this response
 *
 * @throws DavException if this response doesn't contain the expected resource data or an ETag
 * @throws IllegalArgumentException if this response is not successful (callers must filter beforehand)
 */
suspend fun Response.asMultiGetItem(getContent: (Response) -> String?): WebDavCollection.MultiGetItem {
    val response = this
    require(response.isSuccess()) { "Must only be called for successful responses" }

    return response.href.withExceptionContext {
        val content = getContent(response)
            ?: throw DavException("Received multi-get response without data")
        val eTag = response[GetETag::class.java]?.eTag
            ?: throw DavException("Received multi-get response without ETag")
        WebDavCollection.MultiGetItem(
            url = response.href,
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
