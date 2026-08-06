/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.Response
import at.bitfire.dav4jvm.property.caldav.GetCTag
import at.bitfire.dav4jvm.property.webdav.SyncToken
import at.bitfire.davdroid.resource.SyncState

/**
 * Extracts the [SyncState] (`sync-token` or `CTag`) reported by this response, if any.
 */
internal fun Response.syncState(): SyncState? =
    this[SyncToken::class.java]?.token?.let {
        SyncState(SyncState.Type.SYNC_TOKEN, it)
    } ?: this[GetCTag::class.java]?.cTag?.let {
        SyncState(SyncState.Type.CTAG, it)
    }
