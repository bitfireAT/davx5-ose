/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

/**
 * Result of a sync operation from [Syncer], used by [at.bitfire.davdroid.sync.worker.BaseSyncWorker]
 * to determine whether there will be retries.
 */
data class SyncResult(
    var hardError: Boolean = false,
    var softError: Boolean = false,
    var delayUntil: Long = 0
) {

    /**
     * Whether a hard or a soft error occurred.
     */
    fun hasError(): Boolean =
        hardError || softError

}