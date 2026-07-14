/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.content.Context
import androidx.work.WorkInfo
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.toAndroidAccount
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.sync.adapter.SyncFrameworkIntegration
import at.bitfire.davdroid.sync.worker.OneTimeSyncWorker
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class AccountProgressUseCase @Inject constructor(
    @ApplicationContext val context: Context,
    private val syncFramework: SyncFrameworkIntegration,
    private val syncWorkerManager: SyncWorkerManager
) {

    /**
     * Returns the current sync state of the account.
     */
    operator fun invoke(
        accountId: AccountId,
        serviceFlow: Flow<Service?>,
        dataTypes: Iterable<SyncDataType>
    ): Flow<AccountProgress> {
        val serviceRefreshing = isServiceRefreshing(serviceFlow)
        val syncEnqueued = isSyncEnqueued(accountId, dataTypes)
        val syncPending = syncFramework.isSyncPending(accountId.toAndroidAccount(), dataTypes)
        val syncRunning = isSyncRunning(accountId, dataTypes)

        return combine(
            serviceRefreshing,
            syncEnqueued,
            syncPending,
            syncRunning
        ) { refreshing, enqueued, pending, syncing ->
            when {
                refreshing || syncing -> AccountProgress.Active
                enqueued || pending -> AccountProgress.Pending
                else -> AccountProgress.Idle
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun isServiceRefreshing(serviceFlow: Flow<Service?>): Flow<Boolean> =
        serviceFlow.flatMapLatest { service ->
            if (service == null)
                flowOf(false)
            else
                RefreshCollectionsWorker.existsFlow(context, RefreshCollectionsWorker.workerName(service.id))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun isSyncEnqueued(accountId: AccountId, dataTypes: Iterable<SyncDataType>): Flow<Boolean> {
        val account = accountId.toAndroidAccount()
        return syncWorkerManager.hasAnyFlow(
            workStates = listOf(WorkInfo.State.ENQUEUED),
            account = account,
            dataTypes = dataTypes,
            whichTag = { _, authority ->
                // we are only interested in enqueued OneTimeSyncWorkers because there's always an enqueued PeriodicSyncWorker
                OneTimeSyncWorker.workerName(account, authority)
            }
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun isSyncRunning(accountId: AccountId, dataTypes: Iterable<SyncDataType>): Flow<Boolean> =
        syncWorkerManager.hasAnyFlow(
            workStates = listOf(WorkInfo.State.RUNNING),
            account = accountId.toAndroidAccount(),
            dataTypes = dataTypes
        )

}