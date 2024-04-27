/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.app.Application
import androidx.work.WorkInfo
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.syncadapter.BaseSyncWorker
import at.bitfire.davdroid.syncadapter.OneTimeSyncWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class AccountProgressUseCase @Inject constructor(
    val context: Application
) {

    operator fun invoke(
        account: Account,
        serviceFlow: Flow<Service?>,
        authoritiesFlow: Flow<List<String>>
    ): Flow<AccountProgress> {
        val serviceRefreshing = isServiceRefreshing(serviceFlow)
        val syncPending = isSyncPending(account, authoritiesFlow)
        val syncRunning = isSyncRunning(account, authoritiesFlow)

        return combine(serviceRefreshing, syncPending, syncRunning) { refreshing, pending, syncing ->
            when {
                refreshing || syncing -> AccountProgress.Active
                pending -> AccountProgress.Pending
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
    fun isSyncPending(account: Account, authoritiesFlow: Flow<List<String>>): Flow<Boolean> =
        authoritiesFlow.flatMapLatest { authorities ->
            BaseSyncWorker.existsFlow(
                context = context,
                workStates = listOf(WorkInfo.State.ENQUEUED),
                account = account,
                authorities = authorities,
                whichTag = { _, authority ->
                    // we are only interested in pending OneTimeSyncWorkers because there's always a pending PeriodicSyncWorker
                    OneTimeSyncWorker.workerName(account, authority)
                }
            )
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun isSyncRunning(account: Account, authoritiesFlow: Flow<List<String>>): Flow<Boolean> =
        authoritiesFlow.flatMapLatest { authorities ->
            BaseSyncWorker.existsFlow(
                context = context,
                workStates = listOf(WorkInfo.State.RUNNING),
                account = account,
                authorities = authorities
            )
        }

}