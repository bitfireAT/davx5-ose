package at.bitfire.davdroid.ui

import android.accounts.Account
import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.syncadapter.AccountRepository
import at.bitfire.davdroid.syncadapter.BaseSyncWorker
import at.bitfire.davdroid.syncadapter.OneTimeSyncWorker
import at.bitfire.davdroid.syncadapter.SyncUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.text.Collator
import javax.inject.Inject

@HiltViewModel
class AccountsModel @Inject constructor(
    val context: Application,
    accountRepository: AccountRepository,
    private val db: AppDatabase
): ViewModel() {

    // UI state

    /** Tri-state enum to represent active / pending / idle status */
    enum class Progress {
        Active,     // syncing or refreshing
        Pending,    // sync pending
        Idle
    }

    data class AccountInfo(
        val name: Account,
        val progress: Progress
    )

    val showAddAccount: Flow<Boolean> =
        flowOf(true)


    // needed to calculate UI state

    private val accounts = accountRepository.getAllFlow()

    private val workManager = WorkManager.getInstance(context)
    private val runningWorkers = workManager.getWorkInfosFlow(WorkQuery.fromStates(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING))

    val accountInfos: Flow<List<AccountInfo>> = combine(accounts, runningWorkers) { accounts, workInfos ->
        val authorities = SyncUtils.syncAuthorities(context)
        val collator = Collator.getInstance()

        accounts
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
            .map { account ->
                val services = db.serviceDao().getIdsByAccountAsync(account.name)
                val progress = when {
                    workInfos.any { info ->
                        info.state == WorkInfo.State.RUNNING && (
                                services.any { serviceId ->
                                    info.tags.contains(RefreshCollectionsWorker.workerName(serviceId))
                                } || authorities.any { authority ->
                                    info.tags.contains(BaseSyncWorker.commonTag(account, authority))
                                }
                                )
                    } -> Progress.Active

                    workInfos.any { info ->
                        info.state == WorkInfo.State.ENQUEUED && authorities.any { authority ->
                            info.tags.contains(OneTimeSyncWorker.workerName(account, authority))
                        }
                    } -> Progress.Pending

                    else -> Progress.Idle
                }

                AccountInfo(account, progress)
            }
    }


    // actions

    fun syncAllAccounts() {
        /*if (Build.VERSION.SDK_INT >= 25)
            context.getSystemService<ShortcutManager>()?.reportShortcutUsed(UiUtils.SHORTCUT_SYNC_ALL)

        // Enqueue sync worker for all accounts and authorities. Will sync once internet is available
        for (account in allAccounts())
            OneTimeSyncWorker.enqueueAllAuthorities(context, account, manual = true)*/
    }


    // helpers

    /*private fun allAccounts() =
        AccountManager.get(context).getAccountsByType(accountType)*/

}