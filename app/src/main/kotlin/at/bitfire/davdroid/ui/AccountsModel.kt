package at.bitfire.davdroid.ui

import android.accounts.Account
import android.app.Application
import android.content.pm.ShortcutManager
import android.os.Build
import androidx.core.content.getSystemService
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
import kotlinx.coroutines.flow.map
import java.text.Collator
import javax.inject.Inject

@HiltViewModel
class AccountsModel @Inject constructor(
    val context: Application,
    val accountRepository: AccountRepository,
    private val db: AppDatabase
): ViewModel() {

    // UI state

    /** Tri-state enum to represent active / pending / idle status */
    enum class Progress {
        Active,     // syncing or refreshing
        Pending,    // sync pending
        Idle
    }

    enum class FABStyle {
        WithText,
        Standard,
        None
    }

    data class AccountInfo(
        val name: Account,
        val progress: Progress
    )

    private val accounts = accountRepository.getAllFlow()
    val showAddAccount: Flow<FABStyle> = accounts.map {
        if (it.isEmpty())
            FABStyle.WithText
        else
            FABStyle.Standard
    }
    val showSyncAll: Flow<Boolean> = accounts.map { it.isNotEmpty() }

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
        if (Build.VERSION.SDK_INT >= 25)
            context.getSystemService<ShortcutManager>()?.reportShortcutUsed(UiUtils.SHORTCUT_SYNC_ALL)

        // Enqueue sync worker for all accounts and authorities. Will sync once internet is available
        for (account in accountRepository.getAll())
            OneTimeSyncWorker.enqueueAllAuthorities(context, account, manual = true)
    }

}