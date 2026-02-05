/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

/**
 * This class represents the results of a sync operation from [Syncer].
 *
 * Used by [at.bitfire.davdroid.sync.worker.BaseSyncWorker] to determine whether or not there will be retries etc.
 */
data class SyncResult(
    // hard errors by Syncer
    var contentProviderError: Boolean = false,
    var localStorageError: Boolean = false,

    // hard errors by SyncManager
    var numAuthExceptions: Long = 0,
    var numHttpExceptions: Long = 0,
    var numUnclassifiedErrors: Long = 0,

    // soft errors by SyncMAnager
    var numDeadObjectExceptions: Long = 0,
    var numIoExceptions: Long = 0,
    var numServiceUnavailableExceptions: Long = 0,

    // Other values
    var delayUntil: Long = 0
) {

    /**
     * Whether a hard error occurred.
     */
    fun hasHardError(): Boolean =
        contentProviderError
            || localStorageError
            || numAuthExceptions > 0
            || numHttpExceptions > 0
            || numUnclassifiedErrors > 0

    /**
     * Whether a soft error occurred.
     */
    fun hasSoftError(): Boolean =
        numDeadObjectExceptions > 0
            || numIoExceptions > 0
            || numServiceUnavailableExceptions > 0

    /**
     * Whether a hard or a soft error occurred.
     */
    fun hasError(): Boolean =
        hasHardError() || hasSoftError()

}