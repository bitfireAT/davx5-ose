/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.algorithm

import at.bitfire.davdroid.resource.SyncState

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
