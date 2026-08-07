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
 * Extracts the [SyncState] (`sync-token` or `CTag`) reported by this response, if any.
 */
internal fun Response.syncState(): SyncState? =
    this[SyncToken::class.java]?.token?.let {
        SyncState(SyncState.Type.SYNC_TOKEN, it)
    } ?: this[GetCTag::class.java]?.cTag?.let {
        SyncState(SyncState.Type.CTAG, it)
    }

/**
 * Turns this multi-get response into a [WebDavCollection.MultiGetItem], or `null` if the
 * response was not successful.
 *
 * Callers should only invoke this for responses whose [Response.HrefRelation] is `MEMBER`
 * (a collection's self-response or unrelated responses naturally lack the requested content
 * and shouldn't be passed here).
 *
 * @param getContent extracts the resource data from a (successful) response (for instance calendar-data or address-data)
 *
 * @throws DavException if the response is successful but doesn't contain the expected resource data or an ETag
 */
suspend fun Response.asMultiGetItem(getContent: (Response) -> String?): WebDavCollection.MultiGetItem? {
    val response = this
    return response.href.withExceptionContext {
        if (!response.isSuccess())
            return@withExceptionContext null
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
