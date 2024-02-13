/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.annotation.IntDef
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.ConnectionUtils.internetAvailable
import at.bitfire.davdroid.network.ConnectionUtils.wifiAvailable
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.davdroid.ui.NotificationUtils.notifyIfPossible
import at.bitfire.davdroid.ui.account.WifiPermissionsActivity
import at.bitfire.davdroid.util.PermissionUtils
import at.bitfire.ical4android.TaskProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.logging.Level

/**
 * Handles immediate sync requests and cancellations of accounts and respective content authorities,
 * by creating appropriate workers.
 *
 * The different sync workers each carry a unique work name composed of the account and authority they
 * are syncing. See [SyncWorker.workerName] for more information.
 *
 * By enqueuing this worker ([SyncWorker.enqueue]) a sync will be started immediately (as soon as
 * possible). Currently, there are three scenarios starting a sync:
 *
 * 1) *manual sync*: User presses an in-app sync button and enqueues this worker directly.
 * 2) *periodic sync*: User defines time interval to sync in app settings. The [PeriodicSyncWorker] runs
 * in the background and enqueues this worker when due.
 * 3) *content-triggered sync*: User changes a calendar event, task or contact, or presses a sync
 * button in one of the responsible apps. The [SyncAdapterService] is notified of this and enqueues
 * this worker.
 *
 * Expedited: when run manually
 *
 * Long-running: yes (sync make take long when a lot of data is transferred over a slow connection/server).
 * Needs a foreground service, whose launch from background is restricted since Android 12. So this will only
 * work when the app is in foreground or battery optimizations are turned off.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {

        // Worker input parameters
        internal const val ARG_ACCOUNT_NAME = "accountName"
        internal const val ARG_ACCOUNT_TYPE = "accountType"
        internal const val ARG_AUTHORITY = "authority"

        /** Boolean. Set to `true` when the job was requested as expedited job. */
        private const val ARG_EXPEDITED = "expedited"

        private const val ARG_UPLOAD = "upload"

        private const val ARG_RESYNC = "resync"
        @IntDef(NO_RESYNC, RESYNC, FULL_RESYNC)
        annotation class ArgResync
        const val NO_RESYNC = 0
        const val RESYNC = 1
        const val FULL_RESYNC = 2

        /**
         * How often this work will be retried to run after soft (network) errors.
         *
         * Retry strategy is defined in work request ([enqueue]).
         */
        internal const val MAX_RUN_ATTEMPTS = 5

        /**
         * Unique work name of this worker. Can also be used as tag.
         *
         * Mainly used to query [WorkManager] for work state (by unique work name or tag).
         *
         * *NOTE:* SyncWorkers for address book accounts bear the unique worker name of their parent
         * account (main account) as tag. This makes it easier to query the overall sync status of a
         * main account.
         *
         * @param account the account this worker is running for
         * @param authority the authority this worker is running for
         * @return Name of this worker composed as "sync $authority ${account.type}/${account.name}"
         */
        fun workerName(account: Account, authority: String) =
            "sync $authority ${account.type}/${account.name}"

        /**
         * Requests immediate synchronization of an account with all applicable
         * authorities (contacts, calendars, …).
         *
         * @see enqueue
         */
        fun enqueueAllAuthorities(
            context: Context,
            account: Account,
            @ArgResync resync: Int = NO_RESYNC,
            upload: Boolean = false
        ) {
            for (authority in SyncUtils.syncAuthorities(context))
                enqueue(context, account, authority, expedited = true, resync = resync, upload = upload)
        }

        /**
         * Requests immediate synchronization of an account with a specific authority.
         *
         * @param account       account to sync
         * @param authority     authority to sync (for instance: [CalendarContract.AUTHORITY])
         * @param resync        whether to request (full) re-synchronization or not
         * @param upload        see [ContentResolver.SYNC_EXTRAS_UPLOAD] used only for contacts sync
         *                      and android 7 workaround
         * @return existing or newly created worker name
         */
        fun enqueue(
            context: Context,
            account: Account,
            authority: String,
            expedited: Boolean,
            @ArgResync resync: Int = NO_RESYNC,
            upload: Boolean = false
        ): String {
            // Worker arguments
            val argumentsBuilder = Data.Builder()
                .putString(ARG_AUTHORITY, authority)
                .putString(ARG_ACCOUNT_NAME, account.name)
                .putString(ARG_ACCOUNT_TYPE, account.type)
            if (expedited)
                argumentsBuilder.putBoolean(ARG_EXPEDITED, true)
            if (resync != NO_RESYNC)
                argumentsBuilder.putInt(ARG_RESYNC, resync)
            argumentsBuilder.putBoolean(ARG_UPLOAD, upload)

            // build work request
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)   // require a network connection
                .build()
            val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .addTag(workerName(account, authority))
                .setInputData(argumentsBuilder.build())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS,   // 30 sec
                    TimeUnit.MILLISECONDS
                )
                .setConstraints(constraints)
                .apply {
                    // If this is a sub sync worker (address book sync), add the main account tag as well
                    if (account.type == context.getString(R.string.account_type_address_book)) {
                        val mainAccount = LocalAddressBook.mainAccount(context, account)
                        addTag(workerName(mainAccount, authority))
                    }
                }

            if (expedited)
                workRequest.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)

            // enqueue and start syncing
            val name = workerName(account, authority)
            val request = workRequest.build()
            Logger.log.log(Level.INFO, "Enqueueing unique worker: $name, expedited = $expedited, tags = ${request.tags}")
            WorkManager.getInstance(context).enqueueUniqueWork(
                name,
                ExistingWorkPolicy.KEEP,    // If sync is already running, just continue.
                                            // Existing retried work will not be replaced (for instance when
                                            // PeriodicSyncWorker enqueues another scheduled sync).
                request
            )
            return name
        }

        /**
         * Stops running sync worker or removes pending sync from queue, for all authorities.
         */
        fun cancelSync(context: Context, account: Account) {
            for (authority in SyncUtils.syncAuthorities(context))
                WorkManager.getInstance(context).cancelUniqueWork(workerName(account, authority))
        }

        /**
         * Will tell whether >0 [SyncWorker] exists, belonging to given account and authorities,
         * and which are/is in the given worker state.
         *
         * @param workStates   list of states of workers to match
         * @param account      the account which the workers belong to
         * @param authorities  type of sync work, ie [CalendarContract.AUTHORITY]
         * @return *true* if at least one worker with matching query was found; *false* otherwise
         */
        fun exists(
            context: Context,
            workStates: List<WorkInfo.State>,
            account: Account? = null,
            authorities: List<String>? = null
        ): LiveData<Boolean> {
            val workQuery = WorkQuery.Builder
                .fromStates(workStates)
            if (account != null && authorities != null)
                workQuery.addTags(
                    authorities.map { authority -> workerName(account, authority) }
                )
            return WorkManager.getInstance(context)
                .getWorkInfosLiveData(workQuery.build()).map { workInfoList ->
                    workInfoList.isNotEmpty()
                }
        }


        // connection checks

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

        /**
         * Checks whether user imposed sync conditions from settings are met:
         * - Sync only on WiFi?
         * - Sync only on specific WiFi (SSID)?
         *
         * @param accountSettings Account settings of the account to check (and is to be synced)
         * @return *true* if conditions are met; *false* if not
         */
        internal fun wifiConditionsMet(context: Context, accountSettings: AccountSettings): Boolean {
            // May we sync without WiFi?
            if (!accountSettings.getSyncWifiOnly())
                return true     // yes, continue

            // WiFi required, is it available?
            val connectivityManager = context.getSystemService<ConnectivityManager>()!!
            if (!wifiAvailable(connectivityManager)) {
                Logger.log.info("Not on connected WiFi, stopping")
                return false
            }
            // If execution reaches this point, we're on a connected WiFi

            // Check whether we are connected to the correct WiFi (in case SSID was provided)
            return correctWifiSsid(context, accountSettings)
        }

    }


    private val dispatcher = SyncWorkDispatcher.getInstance(applicationContext)
    private val notificationManager = NotificationManagerCompat.from(applicationContext)

    override suspend fun doWork(): Result = withContext(dispatcher) {
        // ensure we got the required arguments
        val account = Account(
            inputData.getString(ARG_ACCOUNT_NAME) ?: throw IllegalArgumentException("$ARG_ACCOUNT_NAME required"),
            inputData.getString(ARG_ACCOUNT_TYPE) ?: throw IllegalArgumentException("$ARG_ACCOUNT_TYPE required")
        )
        val authority = inputData.getString(ARG_AUTHORITY) ?: throw IllegalArgumentException("$ARG_AUTHORITY required")
        val expedited = inputData.getBoolean(ARG_EXPEDITED, false)

        // this is a long-running worker
        try {
            setForeground(getForegroundInfo())
        } catch (e: IllegalStateException) {
            Logger.log.log(Level.WARNING, "Couldn't create foreground service for sync worker. This is not necessarily an error, " +
                    "but battery optimizations should be disabled to avoid this.", e)
        }

        // check internet connection
        val ignoreVpns = AccountSettings(applicationContext, account).getIgnoreVpns()
        val connectivityManager = applicationContext.getSystemService<ConnectivityManager>()!!
        if (!internetAvailable(connectivityManager, ignoreVpns)) {
            Logger.log.info("WorkManager started SyncWorker without Internet connection. Aborting.")
            return@withContext Result.failure()
        }

        Logger.log.info("Running sync worker: account=$account, authority=$authority")

        // What are we going to sync? Select syncer based on authority
        val syncer: Syncer = when (authority) {
            applicationContext.getString(R.string.address_books_authority) ->
                AddressBookSyncer(applicationContext, expedited)
            CalendarContract.AUTHORITY ->
                CalendarSyncer(applicationContext)
            ContactsContract.AUTHORITY ->
                ContactSyncer(applicationContext)
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
        when (inputData.getInt(ARG_RESYNC, NO_RESYNC)) {
            RESYNC ->      extras.add(Syncer.SYNC_EXTRAS_RESYNC)
            FULL_RESYNC -> extras.add(Syncer.SYNC_EXTRAS_FULL_RESYNC)
        }
        if (inputData.getBoolean(ARG_UPLOAD, false))
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

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationUtils.newBuilder(applicationContext, NotificationUtils.CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_foreground_notify)
            .setContentTitle(applicationContext.getString(R.string.foreground_service_notify_title))
            .setContentText(applicationContext.getString(R.string.foreground_service_notify_text))
            .setStyle(NotificationCompat.BigTextStyle())
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ForegroundInfo(NotificationUtils.NOTIFY_SYNC_EXPEDITED, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else
            ForegroundInfo(NotificationUtils.NOTIFY_SYNC_EXPEDITED, notification)
    }

}