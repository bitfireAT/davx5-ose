/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.app.Application
import androidx.work.WorkInfo
import at.bitfire.davdroid.syncadapter.BaseSyncWorker
import at.bitfire.davdroid.syncadapter.OneTimeSyncWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

class ExistsSyncWorkerUseCase @Inject constructor(
    val context: Application
) {

    enum class RequestedState {
        PENDING,
        RUNNING
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(
        account: Account,
        authoritiesFlow: Flow<List<String>>,
        requestedState: RequestedState
    ): Flow<Boolean> =
        authoritiesFlow.flatMapLatest { authorities ->
            when (requestedState) {
                RequestedState.PENDING ->
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

                RequestedState.RUNNING ->
                    BaseSyncWorker.existsFlow(
                        context = context,
                        workStates = listOf(WorkInfo.State.RUNNING),
                        account = account,
                        authorities = authorities
                    )
            }
        }

}