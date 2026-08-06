/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource.remote

import at.bitfire.dav4jvm.ktor.DavCollection
import at.bitfire.davdroid.resource.SyncState

/**
 * Provides remote collection access as required for [at.bitfire.davdroid.sync.SyncManager].
 *
 * Currently, most call sites just call through [davCollection], but the goal is to provide all remote collection
 * operations that are required for synchronization here.
 */
interface WebDavCollection {

    val davCollection: DavCollection

    /**
     * Queries the collection's current capabilities. Also fetches the [SyncState]
     * in the same PROPFIND request to save a network round-trip.
     */
    suspend fun queryCapabilities(): QueryCapabilitiesResult

    data class QueryCapabilitiesResult(
        val syncState: SyncState?,
        val capabilities: Capabilities
    )

    data class Capabilities(
        // CalDAV only
        val maxCalResourceSize: Long? = null,

        // CardDAV only
        val maxCardResourceSize: Long? = null,
        val supportsCollectionSync: Boolean = false,
        val supportsVCard4: Boolean = false
    )

}
