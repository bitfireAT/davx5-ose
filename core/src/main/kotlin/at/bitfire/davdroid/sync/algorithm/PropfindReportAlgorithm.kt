/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.algorithm

import at.bitfire.dav4jvm.ktor.MultiStatusItem
import at.bitfire.davdroid.resource.SyncState
import at.bitfire.davdroid.sync.MultiResponseCallback
import kotlinx.coroutines.flow.Flow
import java.util.logging.Logger

/**
 * Full listing (PROPFIND/REPORT), diff against local ETags, then delete what's no longer
 * present remotely.
 */
class PropfindReportAlgorithm(
    private val context: Context
) : SyncAlgorithm {

    /** Operations this algorithm needs from the owning [at.bitfire.davdroid.sync.SyncManager]. */
    class Context(
        val resetPresentRemotely: () -> Unit,
        val querySyncState: suspend () -> SyncState?,
        val syncRemote: suspend (suspend (MultiResponseCallback) -> Unit) -> Unit,
        val listAllRemote: () -> Flow<MultiStatusItem>,
        val deleteNotPresentRemotely: suspend () -> Unit,
        val postProcess: suspend () -> Unit,
        val setLastSyncState: (SyncState?) -> Unit
    )

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override suspend operator fun invoke(modificationsPresent: Boolean, remoteSyncState: SyncState?) {
        logger.info("Sync algorithm: full listing as one result (PROPFIND/REPORT)")
        context.resetPresentRemotely()

        // get current sync state
        var syncState = remoteSyncState
        if (modificationsPresent)
            syncState = context.querySyncState()

        // list and process all entries at current sync state (which may be the same as or newer than remoteSyncState)
        logger.info("Processing remote entries")
        context.syncRemote { callback ->
            context.listAllRemote().forEachResponse(callback)
        }

        logger.info("Deleting entries which are not present remotely anymore")
        context.deleteNotPresentRemotely()

        logger.info("Post-processing")
        context.postProcess()

        logger.info("Saving sync state: $syncState")
        context.setLastSyncState(syncState)
    }

}
