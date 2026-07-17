/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import at.bitfire.davdroid.resource.LocalCollection
import java.util.Optional
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Handles local changes in a read-only collection: instead of pushing them to the server,
 * resets them locally so that they will be downloaded again and overwritten with the server's version.
 */
class ReadOnlyPolicy @Inject constructor(
    private val logger: Logger
) {

    /**
     * Restores locally deleted resources from the server instead of deleting them from the (read-only) server.
     *
     * @return whether local resources have been restored so that a synchronization is always necessary
     */
    suspend fun resetDeleted(collection: LocalCollection<*>): Boolean {
        var modified = false
        collection.findDeleted().collect { local ->
            logger.warning("Restoring locally deleted resource (read-only collection!)")
            SyncException.wrapWithLocalResource(local) {
                local.resetDeleted()
            }
            modified = true
        }

        /* When a resource has been inserted to a read-only collection it's not enough to force
        synchronization (by returning true), we also need to make sure all resources are downloaded again
        (force full re-sync) so that local additions are deleted again because they're not on the server. */
        if (modified)
            collection.lastSyncState = null

        return modified
    }

    /**
     * Resets locally modified entries to ETag=null instead of uploading them to the (read-only) server.
     *
     * @return whether local resources have been reset so that a synchronization is always necessary
     */
    suspend fun resetDirty(collection: LocalCollection<*>): Boolean {
        var modified = false
        collection.findDirty().collect { local ->
            logger.warning("Resetting locally modified resource to ETag=null (read-only collection!)")
            SyncException.wrapWithLocalResource(local) {
                local.clearDirty(
                    fileName = Optional.empty(),
                    eTag = null,
                    scheduleTag = null
                )
            }
            modified = true
        }

        /* When a resource has been inserted to a read-only collection it's not enough to force
        synchronization (by returning true), we also need to make sure all resources are downloaded again
        (force full re-sync) so that local additions are overwritten by the server items. */
        if (modified)
            collection.lastSyncState = null

        return modified
    }

}
