/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.algorithm

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.ktor.MultiStatusItem
import at.bitfire.davdroid.resource.SyncState
import at.bitfire.davdroid.sync.MultiResponseCallback
import kotlinx.coroutines.flow.Flow

/**
 * A pluggable synchronization algorithm, run by [at.bitfire.davdroid.sync.SyncManager] once it has been determined
 * that a sync run is necessary.
 */
interface SyncAlgorithm {

    /**
     * Runs the algorithm.
     *
     * @param modificationsPresent  whether local changes have already been uploaded in this run
     *                              (if so, the remote sync state must be re-queried)
     * @param remoteSyncState       sync state obtained by `queryCapabilities()`, before any local changes were uploaded
     */
    suspend operator fun invoke(modificationsPresent: Boolean, remoteSyncState: SyncState?)

}

/**
 * Bridges a [Flow] of [MultiStatusItem]s back to callback style, invoking [callback]
 * for every [MultiStatusItem.Response] the flow emits.
 *
 * This is a temporary adapter to keep the sync algorithms close to their pre-[Flow] shape.
 * The goal is to migrate them to work with [Flow] directly and drop callback-style
 * processing (and this helper) entirely.
 *
 * @return properties found outside `<response>` elements (for instance `sync-token`)
 */
internal suspend fun Flow<MultiStatusItem>.forEachResponse(
    callback: MultiResponseCallback
): List<Property> {
    val extraProperties = mutableListOf<Property>()
    collect { item ->
        when (item) {
            is MultiStatusItem.Response -> callback(item.response, item.relation)
            is MultiStatusItem.ExtraProperty -> extraProperties += item.property
        }
    }
    return extraProperties
}
