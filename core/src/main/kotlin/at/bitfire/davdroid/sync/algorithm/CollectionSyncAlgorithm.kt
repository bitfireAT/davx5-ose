/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.algorithm

import at.bitfire.dav4jvm.Error
import at.bitfire.dav4jvm.ktor.exception.HttpException
import at.bitfire.dav4jvm.property.webdav.SyncToken
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.resource.SyncState
import at.bitfire.davdroid.sync.MultiResponseCallback
import java.util.logging.Logger

/**
 * Incremental collection synchronization (RFC 6578), including initial-sync and
 * invalid-sync-token handling.
 */
class CollectionSyncAlgorithm(
    private val context: Context
) : SyncAlgorithm {

    /** Operations this algorithm needs from the owning [at.bitfire.davdroid.sync.SyncManager]. */
    class Context(
        val getLastSyncState: () -> SyncState?,
        val setLastSyncState: (SyncState?) -> Unit,
        val resetPresentRemotely: () -> Unit,
        val syncRemote: suspend (suspend (MultiResponseCallback) -> Unit) -> Unit,
        val listRemoteChanges: suspend (SyncState?, MultiResponseCallback) -> Pair<SyncToken, Boolean>,
        val deleteNotPresentRemotely: suspend () -> Unit,
        val postProcess: suspend () -> Unit
    )

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override suspend operator fun invoke(modificationsPresent: Boolean, remoteSyncState: SyncState?) {
        var syncState = context.getLastSyncState()?.takeIf { it.type == SyncState.Type.SYNC_TOKEN }

        var initialSync = false
        if (syncState == null) {
            logger.info("Starting initial sync")
            initialSync = true
            context.resetPresentRemotely()
        } else if (syncState.initialSync == true) {
            logger.info("Continuing initial sync")
            initialSync = true
        }

        var furtherChanges = false
        do {
            logger.info("Listing changes since $syncState")
            context.syncRemote { callback ->
                try {
                    val result = context.listRemoteChanges(syncState, callback)
                    syncState = SyncState.fromSyncToken(result.first, initialSync)
                    furtherChanges = result.second
                } catch (e: HttpException) {
                    if (e.errors.contains(Error(WebDAV.ValidSyncToken))) {
                        logger.info("Sync token invalid, performing initial sync")
                        initialSync = true
                        context.resetPresentRemotely()

                        val result = context.listRemoteChanges(null, callback)
                        syncState = SyncState.fromSyncToken(result.first, initialSync)
                        furtherChanges = result.second
                    } else
                        throw e
                }
            }

            logger.info("Saving sync state: $syncState")
            context.setLastSyncState(syncState)

            logger.info("Server has further changes: $furtherChanges")
        } while (furtherChanges)

        if (initialSync) {
            // initial sync is finished, remove all local resources which have not been listed by server
            logger.info("Deleting local resources which are not on server (anymore)")
            context.deleteNotPresentRemotely()

            // remove initial sync flag
            syncState!!.initialSync = false
            logger.info("Initial sync completed, saving sync state: $syncState")
            context.setLastSyncState(syncState)
        }

        logger.info("Post-processing")
        context.postProcess()
    }

}
