/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import io.ktor.http.Url

/**
 * One item reported by a `sync-collection` REPORT (RFC 6578), as returned by [WebDavCollection.listChanges].
 */
sealed class CollectionSyncItem {

    /** The `sync-token` reported for this `sync-collection` REPORT. */
    data class SyncToken(val token: at.bitfire.dav4jvm.property.webdav.SyncToken) : CollectionSyncItem()

    /** Signals that the result set was truncated (507 on the request-URI) and more changes follow. */
    data object FurtherChanges : CollectionSyncItem()

    /** A member that was added or changed (2xx response). */
    data class ChangedMember(val memberState: InternalMemberState) : CollectionSyncItem()

    /** A member that was removed (404 response). Only the href is available, no ETag. */
    data class RemovedMember(val href: Url) : CollectionSyncItem()

}
