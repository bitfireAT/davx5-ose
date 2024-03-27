/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.ConnectionUtils
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.davdroid.ui.NotificationUtils.notifyIfPossible
import at.bitfire.davdroid.ui.account.WifiPermissionsActivity
import at.bitfire.davdroid.util.PermissionUtils
import at.bitfire.ical4android.TaskProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.logging.Level

abstract class BaseSyncWorker(
    appContext: Context,
    val workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {

        // common worker input parameters
        const val INPUT_ACCOUNT_NAME = "accountName"
        const val INPUT_ACCOUNT_TYPE = "accountType"
        const val INPUT_AUTHORITY = "authority"

        /** set to true for user-initiated sync that skips network checks */
        const val INPUT_MANUAL = "manual"

        /**
         * How often this work will be retried to run after soft (network) errors.
         *
         * Retry strategy is defined in work request ([enqueue]).
         */
        internal const val MAX_RUN_ATTEMPTS = 5

        /**
         * Set of currently running syncs, identified by their [commonTag].
         */
        private val runningSyncs = Collections.synchronizedSet(HashSet<String>())

        /**
         * Stops running sync workers and removes pending sync workers from queue, for all authorities.
         */
        fun cancelAllWork(context: Context, account: Account) {
            val workManager = WorkManager.getInstance(context)
            for (authority in SyncUtils.syncAuthorities(context)) {
                workManager.cancelUniqueWork(OneTimeSyncWorker.workerName(account, authority))
                workManager.cancelUniqueWork(PeriodicSyncWorker.workerName(account, authority))
            }
        }

        /**
         * This tag shall be added to every worker that is enqueued by a subclass.
         */
        fun commonTag(account: Account, authority: String): String =
            "sync-$authority ${account.type}/${account.name}"

        /**
         * Will tell whether >0 sync workers (both [PeriodicSyncWorker] and [OneTimeSyncWorker])
         * exist, belonging to given account and authorities, and which are/is in the given worker state.
         *
         * @param workStates   list of states of workers to match
         * @param account      the account which the workers belong to
         * @param authorities  type of sync work, ie [CalendarContract.AUTHORITY]
         * @param whichTag     function to generate tag that should be observed for given account and authority
         * @return *true* if at least one worker with matching query was found; *false* otherwise
         */
        fun exists(
            context: Context,
            workStates: List<WorkInfo.State>,
            account: Account? = null,
            authorities: List<String>? = null,
            whichTag: (account: Account, authority: String) -> String = { account, authority ->
                commonTag(account, authority)
            }
        ): LiveData<Boolean> {
            val workQuery = WorkQuery.Builder
                .fromStates(workStates)
            if (account != null && authorities != null)
                workQuery.addTags(
                    authorities.map { authority -> whichTag(account, authority) }
                )
            return WorkManager.getInstance(context)
                .getWorkInfosLiveData(workQuery.build()).map { workInfoList ->
                    workInfoList.isNotEmpty()
                }
        }

        /**
         * Checks whether user imposed sync conditions from settings are met:
         * - Sync only on WiFi?
         * - Sync only on specific WiFi (SSID)?
         *
         * @param accountSettings Account settings of the account to check (and is to be synced)
         * @return *true* if conditions are met; *false* if not
         */
        fun wifiConditionsMet(context: Context, accountSettings: AccountSettings): Boolean {
            // May we sync without WiFi?
            if (!accountSettings.getSyncWifiOnly())
                return true     // yes, continue

            // WiFi required, is it available?
            val connectivityManager = context.getSystemService<ConnectivityManager>()!!
            if (!ConnectionUtils.wifiAvailable(connectivityManager)) {
                Logger.log.info("Not on connected WiFi, stopping")
                return false
            }
            // If execution reaches this point, we're on a connected WiFi

            // Check whether we are connected to the correct WiFi (in case SSID was provided)
            return correctWifiSsid(context, accountSettings)
        }

        /**
         * Checks whether we are connected to the correct wifi (SSID) defined by user in the
         * account settings.
         *
         * Note: Should be connected to some wifi before calling.
         *
         * @param accountSettings Settings of account to check
         * @return *true* if connected to the correct wifi OR no wifi names were specified in
         * account settings; *false* otherwise
         */
        internal fun correctWifiSsid(context: Context, accountSettings: AccountSettings): Boolean {
            accountSettings.getSyncWifiOnlySSIDs()?.let { onlySSIDs ->
                // check required permissions and location status
                if (!PermissionUtils.canAccessWifiSsid(context)) {
                    // not all permissions granted; show notification
                    val intent = Intent(context, WifiPermissionsActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    intent.putExtra(WifiPermissionsActivity.EXTRA_ACCOUNT, accountSettings.account)
                    PermissionUtils.notifyPermissions(context, intent)

                    Logger.log.warning("Can't access WiFi SSID, aborting sync")
                    return false
                }

                val wifi = context.getSystemService<WifiManager>()!!
                val info = wifi.connectionInfo
                if (info == null || !onlySSIDs.contains(info.ssid.trim('"'))) {
                    Logger.log.info("Connected to wrong WiFi network (${info.ssid}), aborting sync")
                    return false
                }
                Logger.log.fine("Connected to WiFi network ${info.ssid}")
            }
            return true
        }

    }


    private val dispatcher = SyncWorkDispatcher.getInstance(applicationContext)
    private val notificationManager = NotificationManagerCompat.from(applicationContext)


    override suspend fun doWork(): Result {
        // ensure we got the required arguments
        val account = Account(
            inputData.getString(INPUT_ACCOUNT_NAME) ?: throw IllegalArgumentException("$INPUT_ACCOUNT_NAME required"),
            inputData.getString(INPUT_ACCOUNT_TYPE) ?: throw IllegalArgumentException("$INPUT_ACCOUNT_TYPE required")
        )
        val authority = inputData.getString(INPUT_AUTHORITY) ?: throw IllegalArgumentException("$INPUT_AUTHORITY required")

        val syncTag = commonTag(account, authority)
        Logger.log.info("${javaClass.simpleName} called for $syncTag")

        if (!runningSyncs.add(syncTag)) {
            Logger.log.info("There's already another worker running for $syncTag, skipping")
            return Result.success()
        }

        try {
            val accountSettings = try {
                AccountSettings(applicationContext, account)
            } catch (e: InvalidAccountException) {
                val workId = workerParams.id
                Logger.log.warning("Account $account doesn't exist anymore, cancelling worker $workId")

                val workManager = WorkManager.getInstance(applicationContext)
                workManager.cancelWorkById(workId)

                return Result.failure()
            }

            if (inputData.getBoolean(INPUT_MANUAL, false))
                Logger.log.info("Manual sync, skipping network checks")
            else {
                // check internet connection
                val ignoreVpns = accountSettings.getIgnoreVpns()
                val connectivityManager = applicationContext.getSystemService<ConnectivityManager>()!!
                if (!ConnectionUtils.internetAvailable(connectivityManager, ignoreVpns)) {
                    Logger.log.info("WorkManager started SyncWorker without Internet connection. Aborting.")
                    return Result.success()
                }

                // check WiFi restriction
                if (!wifiConditionsMet(applicationContext, accountSettings)) {
                    Logger.log.info("WiFi conditions not met. Won't run periodic sync.")
                    return Result.success()
                }
            }

            return doSyncWork(account, authority, accountSettings)
        } finally {
            Logger.log.info("${javaClass.simpleName} finished for $syncTag")
            runningSyncs -= syncTag
        }
    }

    open suspend fun doSyncWork(
        account: Account,
        authority: String,
        accountSettings: AccountSettings
    ): Result = withContext(dispatcher) {
        Logger.log.info("Running ${javaClass.name}: account=$account, authority=$authority")

        // What are we going to sync? Select syncer based on authority
        val syncer: Syncer = when (authority) {
            applicationContext.getString(R.string.address_books_authority) ->
                AddressBookSyncer(applicationContext)
            CalendarContract.AUTHORITY ->
                CalendarSyncer(applicationContext)
            TaskProvider.ProviderName.JtxBoard.authority ->
                JtxSyncer(applicationContext)
            TaskProvider.ProviderName.OpenTasks.authority,
            TaskProvider.ProviderName.TasksOrg.authority ->
                TaskSyncer(applicationContext)
            else ->
                throw IllegalArgumentException("Invalid authority $authority")
        }

        // pass possibly supplied flags to the selected syncer
        val extras = mutableListOf<String>()
        when (inputData.getInt(OneTimeSyncWorker.ARG_RESYNC, OneTimeSyncWorker.NO_RESYNC)) {
            OneTimeSyncWorker.RESYNC ->      extras.add(Syncer.SYNC_EXTRAS_RESYNC)
            OneTimeSyncWorker.FULL_RESYNC -> extras.add(Syncer.SYNC_EXTRAS_FULL_RESYNC)
        }
        if (inputData.getBoolean(OneTimeSyncWorker.ARG_UPLOAD, false))
            // Comes in through SyncAdapterService and is used only by ContactsSyncManager for an Android 7 workaround.
            extras.add(ContentResolver.SYNC_EXTRAS_UPLOAD)

        // acquire ContentProviderClient of authority to be synced
        val provider: ContentProviderClient? =
            try {
                applicationContext.contentResolver.acquireContentProviderClient(authority)
            } catch (e: SecurityException) {
                Logger.log.log(Level.WARNING, "Missing permissions to acquire ContentProviderClient for $authority", e)
                null
            }
        if (provider == null) {
            Logger.log.warning("Couldn't acquire ContentProviderClient for $authority")
            return@withContext Result.failure()
        }

        val result = SyncResult()
        provider.use {
            // Start syncing. We still use the sync adapter framework's SyncResult to pass the sync results, but this
            // is only for legacy reasons and can be replaced by an own result class in the future.
            runInterruptible {
                syncer.onPerformSync(account, extras.toTypedArray(), authority, provider, result)
            }
        }

        // Check for errors
        if (result.hasError()) {
            val syncResult = Data.Builder()
                .putString("syncresult", result.toString())
                .putString("syncResultStats", result.stats.toString())
                .build()

            val softErrorNotificationTag = account.type + "-" + account.name + "-" + authority

            // On soft errors the sync is retried a few times before considered failed
            if (result.hasSoftError()) {
                Logger.log.warning("Soft error while syncing: result=$result, stats=${result.stats}")
                if (runAttemptCount < MAX_RUN_ATTEMPTS) {
                    val blockDuration = result.delayUntil - System.currentTimeMillis()/1000
                    Logger.log.warning("Waiting for $blockDuration seconds, before retrying ...")

                    // We block the SyncWorker here so that it won't be started by the sync framework immediately again.
                    // This should be replaced by proper work scheduling as soon as we don't depend on the sync framework anymore.
                    if (blockDuration > 0)
                        delay(blockDuration*1000)

                    Logger.log.warning("Retrying on soft error (attempt $runAttemptCount of $MAX_RUN_ATTEMPTS)")
                    return@withContext Result.retry()
                }

                Logger.log.warning("Max retries on soft errors reached ($runAttemptCount of $MAX_RUN_ATTEMPTS). Treating as failed")

                notificationManager.notifyIfPossible(
                    softErrorNotificationTag,
                    NotificationUtils.NOTIFY_SYNC_ERROR,
                    NotificationUtils.newBuilder(applicationContext, NotificationUtils.CHANNEL_SYNC_IO_ERRORS)
                        .setSmallIcon(R.drawable.ic_sync_problem_notify)
                        .setContentTitle(account.name)
                        .setContentText(applicationContext.getString(R.string.sync_error_retry_limit_reached))
                        .setSubText(account.name)
                        .setOnlyAlertOnce(true)
                        .setPriority(NotificationCompat.PRIORITY_MIN)
                        .setCategory(NotificationCompat.CATEGORY_ERROR)
                        .build()
                )

                return@withContext Result.failure(syncResult)
            }

            // If no soft error found, dismiss sync error notification
            notificationManager.cancel(
                softErrorNotificationTag,
                NotificationUtils.NOTIFY_SYNC_ERROR
            )

            // On a hard error - fail with an error message
            // Note: SyncManager should have notified the user
            if (result.hasHardError()) {
                Logger.log.warning("Hard error while syncing: result=$result, stats=${result.stats}")
                return@withContext Result.failure(syncResult)
            }
        }

        return@withContext Result.success()
    }

}