/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import at.bitfire.dav4android.DavResource
import at.bitfire.davdroid.AccountSettings
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.SyncState
import at.bitfire.davdroid.resource.LocalCollection
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.settings.ISettings
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.ical4android.MiscUtils
import java.util.*
import java.util.logging.Level

abstract class SyncManager<out ResourceType: LocalResource, out CollectionType: LocalCollection<ResourceType>>(
        val context: Context,
        val settings: ISettings,
        val account: Account,
        val accountSettings: AccountSettings,
        val extras: Bundle,
        val authority: String,
        val syncResult: SyncResult,
        val localCollection: CollectionType
): AutoCloseable {

    protected val notificationManager = NotificationUtils.createChannels(context)
    abstract val notificationId: Int


    fun performSync() {
        // dismiss previous error notifications
        notificationManager.cancel(localCollection.uid, notificationId)

        try {
            Logger.log.info("Preparing synchronization")
            if (!prepare()) {
                Logger.log.info("No reason to synchronize, aborting")
                return
            }

            Logger.log.info("Querying server capabilities")
            queryCapabilities()

            Logger.log.info("Sending local deletes/updates to server")
            val modificationsSent =
                    processLocallyDeleted() ||
                    uploadDirty()

            if (modificationsSent || syncRequired())
                when (syncAlgorithm()) {
                    SyncAlgorithm.PROPFIND_REPORT -> {
                        Logger.log.info("Sync algorithm: full listing as one result (PROPFIND/REPORT)")
                        resetPresentRemotely()

                        // get current sync state
                        val syncState = syncState(modificationsSent)

                        // list all entries at now current sync state (which may be the same as or newer than lastSyncState)
                        Logger.log.info("Listing remote entries")
                        val remote = listAllRemote()

                        Logger.log.info("Comparing local/remote entries")
                        val changes = compareLocalRemote(syncState, remote)

                        Logger.log.info("Processing remote changes")
                        processRemoteChanges(changes)

                        Logger.log.info("Deleting entries which are not present remotely anymore")
                        deleteNotPresentRemotely()

                        Logger.log.info("Post-processing")
                        postProcess()

                        Logger.log.info("Saving sync state")
                        localCollection.lastSyncState = changes.state
                    }
                    SyncAlgorithm.COLLECTION_SYNC -> {
                        throw UnsupportedOperationException("Collection sync not supported yet")

                        /*val lastSyncState = localCollection.getLastSyncState()?.takeIf { it.type == SyncState.Type.SYNC_TOKEN }
                        val initialSync = lastSyncState == null
                        if (initialSync)
                            resetPresentRemotely()

                        var changes = listRemoteChanges(lastSyncState)
                        do {
                            processRemoteChanges(changes)
                            localCollection.setLastSyncState(changes.state)

                            changes = listRemoteChanges(changes.state)
                        } while(changes.furtherChanges)

                        if (initialSync)
                            deleteNotPresentRemotely()

                        postProcess()*/
                    }
                }
            else
                Logger.log.info("Remote collection didn't change, no reason to sync")

        } catch(e: Exception) {
            Logger.log.log(Level.SEVERE, "SYNC ERROR", e)
        }
    }

    protected abstract fun prepare(): Boolean

    /**
     * Queries the server for synchronization capabilities like specific report types,
     * data formats etc.
     */
    protected abstract fun queryCapabilities()

    protected abstract fun processLocallyDeleted(): Boolean
    protected abstract fun uploadDirty(): Boolean

    protected abstract fun syncRequired(): Boolean
    protected abstract fun syncAlgorithm(): SyncAlgorithm
    protected abstract fun syncState(forceRefresh: Boolean): SyncState?

    /**
     * Marks all local resources which shall be taken into consideration for this
     * sync as "synchronizing". Purpose of marking is that resources which have been marked
     * and are not present remotely anymore can be deleted.
     *
     * Used together with [deleteNotPresentRemotely].
     */
    protected abstract fun resetPresentRemotely()

    /**
     * Lists all remote resources which should be taken into account for synchronization.
     * Will be used if incremental synchronization is not available.
     * @return Map with resource names (like "mycontact.vcf") as keys and the resources
     */
    protected abstract fun listAllRemote(): Map<String, DavResource>


    /**
     * Compares local resources which are marked for synchronization and remote resources by file name and ETag.
     * Remote resources
     *   + which are not present locally
     *   + whose ETag has changed since the last sync (i.e. remote ETag != locally known last remote ETag)
     * will be saved as "updated" in the result.
     *
     * Must mark all found remote resources as "present remotely", so that a later execution of
     * [deleteNotPresentRemotely] doesn't (locally) delete any currently available remote resources.
     *
     * @param remoteResources Map of remote resource names and resources
     * @return List of updated resources on the server. The "deleted" list remains empty. The sync
     *   state is taken from [syncState].
     */
    protected abstract fun compareLocalRemote(syncState: SyncState?, remoteResources: Map<String, DavResource>): RemoteChanges

    /**
     * Lists remote changes (incremental sync).
     *
     * Must mark all found remote resources as "present remotely", so that a later execution of
     * [deleteNotPresentRemotely] doesn't (locally) delete any currently available remote resources.
     *
     * @return List of of remote changes together with the sync state after those changes
     */
    protected abstract fun listRemoteChanges(state: SyncState?): RemoteChanges

    /**
     * Processes remote changes:
     *   + downloads and locally saves remotely updated resources
     *   + locally deletes remotely deleted resources
     * @param List of remotely updated and deleted resources
     */
    protected abstract fun processRemoteChanges(changes: RemoteChanges)

    /**
     * Locally deletes entries which are
     *   1. not dirty and
     *   2. not marked as [LocalResource.FLAG_REMOTELY_PRESENT].
     *
     * Used together with [resetPresentRemotely] when a full listing has been received from
     * the server to locally delete resources which are not present remotely (anymore).
     */
    protected abstract fun deleteNotPresentRemotely()

    /**
     * Post-processing of synchronized entries, for instance contact group membership operations.
     */
    protected abstract fun postProcess()


    /**
     * Throws an [InterruptedException] if the current thread has been interrupted,
     * most probably because synchronization was cancelled by the user.
     * @throws InterruptedException (which will be catched by [performSync])
     * */
    protected fun abortIfCancelled() {
        if (Thread.interrupted())
            throw InterruptedException("Sync was cancelled")
    }


    enum class SyncPhase(val number: Int) {
        PREPARE(0),
        QUERY_CAPABILITIES(1)
    }

    enum class SyncAlgorithm {
        PROPFIND_REPORT,
        COLLECTION_SYNC
    }


    class RemoteChanges(
        val state: SyncState?,
        val furtherChanges: Boolean
    ) {
        val deleted = LinkedList<String>()
        val updated = LinkedList<DavResource>()

        override fun toString() = MiscUtils.reflectionToString(this)
    }

}