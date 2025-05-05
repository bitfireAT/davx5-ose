/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.push

import android.accounts.Account
import androidx.work.WorkInfo
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Collection.Companion.TYPE_ADDRESSBOOK
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.sync.worker.OneTimeSyncWorker
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import com.google.common.annotations.VisibleForTesting
import dagger.Lazy
import java.util.logging.Logger
import javax.inject.Inject

class PushSyncManager @Inject constructor(
    private val accountRepository: AccountRepository,
    private val collectionRepository: DavCollectionRepository,
    private val logger: Logger,
    private val serviceRepository: DavServiceRepository,
    private val syncWorkerManager: SyncWorkerManager,
    private val tasksAppManager: Lazy<TasksAppManager>
) {

    /**
     * Requests a sync for the given collection topic and service ID.
     * @param topic the topic belonging to the collection to sync
     * @param serviceId the ID of the service which the collection belongs to
     */
    suspend fun requestSync(topic: String?, serviceId: Long?) {
        // Sync datatypes of account which the collection supports
        // Future: only sync affected collection
        if (topic != null) {
            logger.info("Got push message with topic $topic")
            collectionRepository.getSyncableByTopic(topic)?.let { collection ->

                // Identify sync data types of collection
                val syncDataTypes = syncDataTypes(collection)

                // Schedule sync for all the types identified
                serviceRepository.get(collection.serviceId)?.let { service ->
                    val account = accountRepository.fromName(service.accountName)
                    requestSync(account, syncDataTypes)
                }
            }
        } else {
            // Fallback when no known topic is present (shouldn't happen)
            val service = serviceId?.let { serviceRepository.getBlocking(it) }
            if (service != null) {
                // Sync account from service with all datatypes
                logger.warning("Got push message without topic and service, requesting sync for all accounts")
                val account = accountRepository.fromName(service.accountName)
                requestSync(account)
            } else {
                // Sync all accounts
                logger.warning("Got push message without topic, requesting sync for all accounts")
                for (account in accountRepository.getAll())
                    requestSync(account)
            }
        }
    }

    /**
     * Request sync for the given account and sync data types if not pending or active already.
     * @param account the account to sync
     * @param syncDataTypes the sync data types to sync, or an empty set to sync all data types
     */
    private fun requestSync(account: Account, syncDataTypes: Set<SyncDataType> = emptySet()) {
        if (isSyncPendingOrActive(account, syncDataTypes)) {
            logger.info("Ignoring push sync, because sync already pending/active for account ${account.name}")
            return
        }
        if (syncDataTypes.isNotEmpty()) {
            // Only sync given datatypes
            for (syncDataType in syncDataTypes)
                syncWorkerManager.enqueueOneTime(account, syncDataType, fromPush = true)
        } else {
            // Sync all datatypes
            syncWorkerManager.enqueueOneTimeAllAuthorities(account, fromPush = true)
        }
    }


    // helpers

    /**
     * Checks whether a one-time (not periodic) sync is enqueued or running for given account and data types
     * @param account the account to check
     * @param dataTypes the data types to check
     * @return true if a one-time sync is enqueued, false otherwise
     */
    fun isSyncPendingOrActive(account: Account, dataTypes: Set<SyncDataType>? = null): Boolean =
        syncWorkerManager.hasAny(
            workStates = listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING),
            account = account,
            dataTypes = dataTypes,  
            workerNames = listOf(
                OneTimeSyncWorker.workerName(account, SyncDataType.CONTACTS),
                OneTimeSyncWorker.workerName(account, SyncDataType.EVENTS),
                OneTimeSyncWorker.workerName(account, SyncDataType.TASKS)
            )
        )
    
    /**
     * Returns the sync data types for the given collection.
     * @param collection the collection to get the sync data types for
     * @return the sync data types for the given collection
     */
    @VisibleForTesting
    internal fun syncDataTypes(collection: Collection) = buildSet {
        // If the type is an address book, add the contacts type
        if (collection.type == TYPE_ADDRESSBOOK)
            add(SyncDataType.CONTACTS)

        // If the collection supports events, add the events type
        if (collection.supportsVEVENT != false)
            add(SyncDataType.EVENTS)

        // If the collection supports tasks, make sure there's a provider installed,
        // and add the tasks type
        if (collection.supportsVJOURNAL != false || collection.supportsVTODO != false)
            if (tasksAppManager.get().currentProvider() != null)
                add(SyncDataType.TASKS)
    }

}