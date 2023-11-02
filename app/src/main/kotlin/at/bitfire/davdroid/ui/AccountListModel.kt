package at.bitfire.davdroid.ui

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.app.Application
import android.content.Context
import android.content.pm.ShortcutManager
import android.os.Build
import androidx.annotation.AnyThread
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.syncadapter.SyncUtils
import at.bitfire.davdroid.syncadapter.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.Collator
import javax.inject.Inject

@HiltViewModel
class AccountListModel @Inject constructor(
    application: Application,
    private val warnings: AppWarningsManager,
    val db: AppDatabase,
): AndroidViewModel(application), OnAccountsUpdateListener {

    data class AccountInfo(
        val account: Account,
        val syncStatus: SyncStatus,
        val refreshing: Boolean
    )

    val context = application

    // Snackbar feedback
    val feedback = MutableLiveData<String>()

    // Warnings
    val globalSyncDisabled = warnings.globalSyncDisabled
    val dataSaverOn = warnings.dataSaverEnabled
    val networkAvailable = warnings.networkAvailable
    val storageLow = warnings.storageLow

    // Accounts
    private val accountManager = AccountManager.get(context)!!
    private val accountsUpdated = MutableLiveData<Boolean>()
    private val syncWorkersActive = SyncWorker.exists(
        context,
        listOf(
            WorkInfo.State.RUNNING,
            WorkInfo.State.ENQUEUED
        )
    )
    private val refreshingAccounts = RefreshCollectionsWorker.existsLive(
        context, listOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED)
    ).switchMap {
        db.serviceDao().getAllLive().map { services ->
            services.filter { service ->
                RefreshCollectionsWorker.exists(
                    context,
                    listOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED),
                    listOf(service.id)
                )
            }.map { it.accountName }
        }
    }

    val accounts = object : MediatorLiveData<List<AccountInfo>>() {
        var nullableRefreshingAccounts: List<String>? = null
        init {
            addSource(accountsUpdated) { recalculate() }
            addSource(syncWorkersActive) { recalculate() }
            addSource(refreshingAccounts) {
                nullableRefreshingAccounts = it
                recalculate()
            }
        }

        fun recalculate() {
            val refreshingAccounts = nullableRefreshingAccounts ?: return
            val collator = Collator.getInstance()

            val sortedAccounts = accountManager
                .getAccountsByType(context.getString(R.string.account_type))
                .sortedArrayWith { a, b ->
                    collator.compare(a.name, b.name)
                }
            val accountsWithInfo = sortedAccounts.map { account ->
                AccountInfo(
                    account,
                    SyncStatus.fromAccount(context, account),
                    account.name in refreshingAccounts
                )
            }
            value = accountsWithInfo
        }
    }

    init {
        // watch accounts
        accountManager.addOnAccountsUpdatedListener(this, null, true)
    }

    @AnyThread
    override fun onAccountsUpdated(newAccounts: Array<out Account>) {
        accountsUpdated.postValue(true)
    }

    override fun onCleared() {
        accountManager.removeOnAccountsUpdatedListener(this)
        warnings.close()
    }

    fun syncAllAccounts(viaShortcut: Boolean = false) {
        if (viaShortcut && Build.VERSION.SDK_INT >= 25)
            context.getSystemService<ShortcutManager>()?.reportShortcutUsed(UiUtils.SHORTCUT_SYNC_ALL)

        // Notify user that sync will get enqueued if we're not connected to the internet
        warnings.networkAvailable.value?.let { networkAvailable ->
            feedback.postValue(context.getString(
                if (networkAvailable)
                    R.string.sync_requested
                else
                    R.string.no_internet_sync_scheduled
            ))
        }

        // Enqueue sync worker for all accounts and authorities. Will sync once internet is available
        val allAccounts = accountManager.getAccountsByType(context.getString(R.string.account_type))
        for (account in allAccounts)
            SyncWorker.enqueueAllAuthorities(context, account)
    }

}

enum class SyncStatus {
    ACTIVE, PENDING, IDLE;

    companion object {
        /**
         * Returns the sync status of a given account. Checks the account itself and possible
         * sub-accounts (address book accounts).
         *
         * @param account       account to check
         *
         * @return sync status of the given account
         */
        fun fromAccount(context: Context, account: Account): SyncStatus {
            // Add contacts authority, so sync status of address-book-accounts is also checked
            val workerNames = SyncUtils.syncAuthorities(context, true).map { authority ->
                SyncWorker.workerName(account, authority)
            }
            val workQuery = WorkQuery.Builder
                .fromTags(workerNames)
                .addStates(listOf(WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED))
                .build()

            val workInfos = WorkManager.getInstance(context).getWorkInfos(workQuery).get()

            return when {
                workInfos.any { workInfo ->
                    workInfo.state == WorkInfo.State.RUNNING
                } -> ACTIVE

                workInfos.any {workInfo ->
                    workInfo.state == WorkInfo.State.ENQUEUED
                } -> PENDING

                else -> IDLE
            }
        }
    }

}