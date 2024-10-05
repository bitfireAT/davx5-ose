package at.bitfire.davdroid.ui

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.PowerManager
import androidx.core.content.getSystemService
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.ViewModel
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.servicedetection.RefreshCollectionsWorker
import at.bitfire.davdroid.sync.SyncUtils
import at.bitfire.davdroid.sync.worker.BaseSyncWorker
import at.bitfire.davdroid.sync.worker.OneTimeSyncWorker
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import at.bitfire.davdroid.ui.account.AccountProgress
import at.bitfire.davdroid.ui.intro.IntroPage
import at.bitfire.davdroid.ui.intro.IntroPageFactory
import at.bitfire.davdroid.util.broadcastReceiverFlow
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.util.logging.Logger

@HiltViewModel(assistedFactory = AccountsModel.Factory::class)
class AccountsModel @AssistedInject constructor(
    @Assisted val syncAccountsOnInit: Boolean,
    private val accountRepository: AccountRepository,
    @ApplicationContext val context: Context,
    private val db: AppDatabase,
    introPageFactory: IntroPageFactory,
    private val logger: Logger,
    private val syncWorkerManager: SyncWorkerManager
): ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(syncAccountsOnInit: Boolean): AccountsModel
    }


    // Accounts UI state

    enum class FABStyle {
        WithText,
        Standard,
        None
    }

    data class AccountInfo(
        val name: Account,
        val progress: AccountProgress
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
                    } -> AccountProgress.Active

                    workInfos.any { info ->
                        info.state == WorkInfo.State.ENQUEUED && authorities.any { authority ->
                            info.tags.contains(OneTimeSyncWorker.workerName(account, authority))
                        }
                    } -> AccountProgress.Pending

                    else -> AccountProgress.Idle
                }

                AccountInfo(account, progress)
            }
    }


    // other UI state

    val showAppIntro: Flow<Boolean> = flow<Boolean> {
        val anyShowAlwaysPage = introPageFactory.introPages.any { introPage ->
            val policy = introPage.getShowPolicy()
            logger.fine("Intro page ${introPage::class.java.name} policy = $policy")

            policy == IntroPage.ShowPolicy.SHOW_ALWAYS
        }

        emit(anyShowAlwaysPage)
    }.flowOn(Dispatchers.Default)


    // warnings

    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
    private val powerManager: PowerManager = context.getSystemService<PowerManager>()!!

    /** whether a usable network connection is available (sync framework won't run synchronization otherwise) */
    val networkAvailable = callbackFlow<Boolean> {
        val networkCallback = object: ConnectivityManager.NetworkCallback() {
            val availableNetworks = hashSetOf<Network>()

            override fun onAvailable(network: Network) {
                availableNetworks += network
                update()
            }

            override fun onLost(network: Network) {
                availableNetworks -= network
                update()
            }

            private fun update() {
                trySend(availableNetworks.isNotEmpty())
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    /** whether battery saver is active */
    val batterySaverActive =
        broadcastReceiverFlow(
            context = context,
            filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            immediate = true
        ).map { powerManager.isPowerSaveMode }

    /** whether data saver is restricting background synchronization ([ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED]) */
    val dataSaverEnabled =
        broadcastReceiverFlow(
            context = context,
            filter = IntentFilter(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED),
            immediate = true
        ).map { connectivityManager.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED }

    /** whether storage is low (prevents sync framework from running synchronization) */
    @Suppress("DEPRECATION")
    val storageLow =
        broadcastReceiverFlow(
            context = context,
            filter = IntentFilter().apply {
                addAction(Intent.ACTION_DEVICE_STORAGE_LOW)
                addAction(Intent.ACTION_DEVICE_STORAGE_OK)
            },
            immediate = false     // "storage low" intent is sticky
        ).map { intent ->
            when (intent.action) {
                Intent.ACTION_DEVICE_STORAGE_LOW -> true
                else -> false
            }
        }


    init {
        if (syncAccountsOnInit)
            syncAllAccounts()
    }

    
    // actions

    fun syncAllAccounts() {
        // report shortcut action to system
        ShortcutManagerCompat.reportShortcutUsed(context, UiUtils.SHORTCUT_SYNC_ALL)

        // Enqueue sync worker for all accounts and authorities. Will sync once internet is available
        for (account in accountRepository.getAll())
            syncWorkerManager.enqueueOneTimeAllAuthorities(account, manual = true)
    }

}