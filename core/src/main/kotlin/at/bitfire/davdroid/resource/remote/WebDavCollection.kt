/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.DavCollection
import at.bitfire.davdroid.resource.SyncState

/**
 * Provides remote collection access as required for [at.bitfire.davdroid.sync.SyncManager].
 *
 * Currently, call sites just call through [davCollection], but the goal is to provide all remote collection
 * operations that are required for synchronization here.
 */
interface WebDavCollection {

    val davCollection: DavCollection

    /**
     * Queries the collection's current sync state and capabilities (in one PROPFIND request).
     */
    suspend fun queryCapabilities(): QueryCapabilitiesResult

    /**
     * Result of [queryCapabilities]: the sync state and the capabilities, fetched together in one
     * PROPFIND request to save a round-trip, but kept as two separate values since they're different
     * concepts (a point-in-time change marker vs. a static server capability).
     */
    data class QueryCapabilitiesResult(
        val syncState: SyncState?,
        val capabilities: Capabilities
    )

    /**
     * Result of [queryCapabilities] — shared by all implementations; properties that a specific
     * collection type doesn't report keep their default value.
     */
    data class Capabilities(
        val maxResourceSize: Long? = null,
        val supportsCollectionSync: Boolean = false,
        /** CardDAV only */
        val supportsVCard4: Boolean = false
    )

}
