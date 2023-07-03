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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.annotation.IntDef
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.work.BackoffPolicy
import androidx.work.Constraints
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
import androidx.work.Worker
import androidx.work.WorkerParameters
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.davdroid.ui.NotificationUtils.notifyIfPossible
import at.bitfire.davdroid.ui.account.WifiPermissionsActivity
import at.bitfire.davdroid.util.PermissionUtils
import at.bitfire.davdroid.util.closeCompat
import at.bitfire.ical4android.TaskProvider
import com.google.common.util.concurrent.ListenableFuture
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import java.util.logging.Level

/**
 * Handles immediate sync requests, status queries and cancellation for one or multiple authorities
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    companion object {

        // Worker input parameters
        internal const val ARG_ACCOUNT_NAME = "accountName"
        internal const val ARG_ACCOUNT_TYPE = "accountType"
        internal const val ARG_AUTHORITY = "authority"

        private const val ARG_UPLOAD = "upload"

        private const val ARG_RESYNC = "resync"
        @IntDef(NO_RESYNC, RESYNC, FULL_RESYNC)
        annotation class ArgResync
        const val NO_RESYNC = 0
        const val RESYNC = 1
        const val FULL_RESYNC = 2

        // This SyncWorker's tag
        const val TAG_SYNC = "sync"

        /**
         * How often this work will be retried to run after soft (network) errors.
         *
         * Retry strategy is defined in work request ([enqueue]).
         */
        internal const val MAX_RUN_ATTEMPTS = 5

        /**
         * Name of this worker.
         * Used to distinguish between other work processes. There must only ever be one worker with the exact same name.
         */
        fun workerName(account: Account, authority: String) =
            "$TAG_SYNC $authority ${account.type}/${account.name}"

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
                enqueue(context, account, authority, resync, upload)
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
            @ArgResync resync: Int = NO_RESYNC,
            upload: Boolean = false
        ): String {
            // Worker arguments
            val argumentsBuilder = Data.Builder()
                .putString(ARG_AUTHORITY, authority)
                .putString(ARG_ACCOUNT_NAME, account.name)
                .putString(ARG_ACCOUNT_TYPE, account.type)
            if (resync != NO_RESYNC)
                argumentsBuilder.putInt(ARG_RESYNC, resync)
            argumentsBuilder.putBoolean(ARG_UPLOAD, upload)

            // build work request
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)   // require a network connection
                .build()
            val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .addTag(TAG_SYNC)
                .setInputData(argumentsBuilder.build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .setConstraints(constraints)
                .build()

            // enqueue and start syncing
            val name = workerName(account, authority)
            Logger.log.log(Level.INFO, "Enqueueing unique worker: $name")
            WorkManager.getInstance(context).enqueueUniqueWork(
                name,
                ExistingWorkPolicy.KEEP,    // If sync is already running, just continue.
                                            // Existing retried work will not be replaced (for instance when
                                            // PeriodicSyncWorker enqueues another scheduled sync).
                workRequest
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
                .fromTags(listOf(TAG_SYNC))
                .addStates(workStates)
            if (account != null && authorities != null)
                workQuery.addUniqueWorkNames(
                    authorities.map { authority -> workerName(account, authority) }
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
        internal fun wifiConditionsMet(context: Context, accountSettings: AccountSettings): Boolean {
            // May we sync without WiFi?
            if (!accountSettings.getSyncWifiOnly())
                return true     // yes, continue

            // WiFi required, is it available?
            if (!wifiAvailable(context)) {
                Logger.log.info("Not on connected WiFi, stopping")
                return false
            }
            // If execution reaches this point, we're on a connected WiFi

            // Check whether we are connected to the correct WiFi (in case SSID was provided)
            return correctWifiSsid(context, accountSettings)
        }

        /**
         * Checks whether we are connected to working WiFi
         */
        internal fun wifiAvailable(context: Context): Boolean {
            val connectivityManager = context.getSystemService<ConnectivityManager>()!!
            connectivityManager.allNetworks.forEach { network ->
                connectivityManager.getNetworkCapabilities(network)?.let { capabilities ->
                    if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                        return true
                }
            }
            return false
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

    private val notificationManager = NotificationManagerCompat.from(applicationContext)

    /** thread which runs the actual sync code (can be interrupted to stop synchronization)  */
    var syncThread: Thread? = null

    override fun doWork(): Result {
        // ensure we got the required arguments
        val account = Account(
            inputData.getString(ARG_ACCOUNT_NAME) ?: throw IllegalArgumentException("$ARG_ACCOUNT_NAME required"),
            inputData.getString(ARG_ACCOUNT_TYPE) ?: throw IllegalArgumentException("$ARG_ACCOUNT_TYPE required")
        )
        val authority = inputData.getString(ARG_AUTHORITY) ?: throw IllegalArgumentException("$ARG_AUTHORITY required")
        Logger.log.info("Running sync worker: account=$account, authority=$authority")

        // What are we going to sync? Select syncer based on authority
        val syncer: Syncer = when (authority) {
            applicationContext.getString(R.string.address_books_authority) ->
                AddressBookSyncer(applicationContext)
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
            return Result.failure()
        }

        // Start syncing. We still use the sync adapter framework's SyncResult to pass the sync results, but this
        // is only for legacy reasons and can be replaced by an own result class in the future.
        val result = SyncResult()
        try {
            syncThread = Thread.currentThread()
            syncer.onPerformSync(account, extras.toTypedArray(), authority, provider, result)
        } catch (e: SecurityException) {
            Logger.log.log(Level.WARNING, "Security exception when opening content provider for $authority")
        } finally {
            provider.closeCompat()
        }

        // Check for errors
        if (result.hasError()) {
            val syncResult = Data.Builder()
                .putString("syncresult", result.toString())
                .putString("syncResultStats", result.stats.toString())
                .build()

            // On soft errors the sync is retried a few times before considered failed
            if (result.hasSoftError()) {
                Logger.log.warning("Soft error while syncing: result=$result, stats=${result.stats}")
                if (runAttemptCount < MAX_RUN_ATTEMPTS) {
                    Logger.log.warning("Retrying on soft error (attempt $runAttemptCount of $MAX_RUN_ATTEMPTS)")
                    return Result.retry()
                }

                Logger.log.warning("Max retries on soft errors reached ($runAttemptCount of $MAX_RUN_ATTEMPTS). Treating as failed")

                notificationManager.notifyIfPossible(
                    NotificationUtils.NOTIFY_SYNC_ERROR,
                    NotificationUtils.newBuilder(applicationContext, NotificationUtils.CHANNEL_SYNC_IO_ERRORS)
                        .setSmallIcon(R.drawable.ic_sync_problem_notify)
                        .setContentTitle(account.name)
                        .setContentText(applicationContext.getString(R.string.sync_error_retry_limit_reached))
                        .setSubText(account.name)
                        .setOnlyAlertOnce(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setCategory(NotificationCompat.CATEGORY_ERROR)
                        .build()
                )

                return Result.failure(syncResult)
            }

            // On a hard error - fail with an error message
            // Note: SyncManager should have notified the user
            if (result.hasHardError()) {
                Logger.log.warning("Hard error while syncing: result=$result, stats=${result.stats}")
                return Result.failure(syncResult)
            }
        }

        return Result.success()
    }

    override fun onStopped() {
        Logger.log.info("Stopping sync thread")
        syncThread?.interrupt()
    }

    override fun getForegroundInfoAsync(): ListenableFuture<ForegroundInfo> =
        CallbackToFutureAdapter.getFuture { completer ->
            val notification = NotificationUtils.newBuilder(applicationContext, NotificationUtils.CHANNEL_STATUS)
                .setSmallIcon(R.drawable.ic_foreground_notify)
                .setContentTitle(applicationContext.getString(R.string.foreground_service_notify_title))
                .setContentText(applicationContext.getString(R.string.foreground_service_notify_text))
                .setStyle(NotificationCompat.BigTextStyle())
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            completer.set(ForegroundInfo(NotificationUtils.NOTIFY_SYNC_EXPEDITED, notification))
        }

}