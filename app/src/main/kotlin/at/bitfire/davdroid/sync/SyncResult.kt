package at.bitfire.davdroid.sync

/**
 * This class represents the results of a sync operation from [Syncer].
 *
 * Used by [at.bitfire.davdroid.sync.worker.BaseSyncWorker] to determine whether or not there will be retries etc.
 */
data class SyncResult(
    var contentProviderError: Boolean = false,
    var localStorageError: Boolean = false,
    var delayUntil: Long = 0,
    val stats: SyncStats = SyncStats()
) {

    /**
     * Whether a hard error occurred.
     */
    fun hasHardError(): Boolean =
        contentProviderError
            || localStorageError
            || stats.numAuthExceptions > 0
            || stats.numHttpExceptions > 0
            || stats.numUnclassifiedErrors > 0

    /**
     * Whether a soft error occurred.
     */
    fun hasSoftError(): Boolean =
        stats.numDeadObjectExceptions > 0
            || stats.numIoExceptions > 0
            || stats.numServiceUnavailableExceptions > 0

    /**
     * Whether a hard or a soft error occurred.
     */
    fun hasError(): Boolean =
        hasHardError() || hasSoftError()

    /**
     * Holds statistics about the sync operation. Used to determine retries. Also useful for
     * debugging and customer support when logged.
     */
    data class SyncStats(
        // Stats
        var numDeletes: Long = 0,
        var numEntries: Long = 0,
        var numInserts: Long = 0,
        var numSkippedEntries: Long = 0,
        var numUpdates: Long = 0,

        // Hard errors
        var numAuthExceptions: Long = 0,
        var numHttpExceptions: Long = 0,
        var numUnclassifiedErrors: Long = 0,

        // Soft errors
        var numDeadObjectExceptions: Long = 0,
        var numIoExceptions: Long = 0,
        var numServiceUnavailableExceptions: Long = 0
    )

}