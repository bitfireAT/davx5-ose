/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.worker

import android.accounts.Account
import android.content.Context
import android.os.Build
import androidx.annotation.IntDef
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import at.bitfire.davdroid.R
import at.bitfire.davdroid.push.PushNotificationManager
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.AddressBookSyncer
import at.bitfire.davdroid.sync.CalendarSyncer
import at.bitfire.davdroid.sync.JtxSyncer
import at.bitfire.davdroid.sync.ResyncType
import at.bitfire.davdroid.sync.SyncConditions
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.sync.SyncResult
import at.bitfire.davdroid.sync.TaskSyncer
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.sync.account.InvalidAccountException
import at.bitfire.davdroid.sync.worker.BaseSyncWorker.Companion.NO_RESYNC
import at.bitfire.davdroid.sync.worker.BaseSyncWorker.Companion.RESYNC_ENTRIES
import at.bitfire.davdroid.sync.worker.BaseSyncWorker.Companion.RESYNC_LIST
import at.bitfire.davdroid.sync.worker.BaseSyncWorker.Companion.commonTag
import at.bitfire.davdroid.ui.NotificationRegistry
import at.bitfire.ical4android.TaskProvider
import dagger.Lazy
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import java.util.Collections
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

abstract class BaseSyncWorker(
    context: Context,
    private val workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    @Inject
    lateinit var accountSettingsFactory: AccountSettings.Factory

    @Inject
    lateinit var addressBookSyncer: AddressBookSyncer.Factory

    @Inject
    lateinit var calendarSyncer: CalendarSyncer.Factory

    @Inject
    lateinit var jtxSyncer: JtxSyncer.Factory

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var notificationRegistry: NotificationRegistry

    @Inject
    lateinit var pushNotificationManager: PushNotificationManager

    @Inject
    lateinit var syncConditionsFactory: SyncConditions.Factory

    @Inject
    lateinit var tasksAppManager: Lazy<TasksAppManager>

    @Inject
    lateinit var taskSyncer: TaskSyncer.Factory


    override suspend fun doWork(): Result {
        // ensure we got the required arguments
        val account = Account(
            inputData.getString(INPUT_ACCOUNT_NAME) ?: throw IllegalArgumentException("INPUT_ACCOUNT_NAME required"),
            inputData.getString(INPUT_ACCOUNT_TYPE) ?: throw IllegalArgumentException("INPUT_ACCOUNT_TYPE required")
        )
        val dataType = SyncDataType.valueOf(inputData.getString(INPUT_DATA_TYPE) ?: throw IllegalArgumentException("INPUT_SYNC_DATA_TYPE required"))

        val syncTag = commonTag(account, dataType)
        logger.info("${javaClass.simpleName} called for $syncTag")

        if (!runningSyncs.add(syncTag)) {
            logger.info("There's already another worker running for $syncTag, skipping")
            return Result.success()
        }

        // Dismiss any pending push notification
        pushNotificationManager.dismiss(account, dataType)

        try {
            val accountSettings = try {
                accountSettingsFactory.create(account)
            } catch (_: InvalidAccountException) {
                val workId = workerParams.id
                logger.warning("No valid account settings for account $account, cancelling worker $workId")

                val workManager = WorkManager.getInstance(applicationContext)
                workManager.cancelWorkById(workId)

                return Result.failure()
            }

            if (inputData.getBoolean(INPUT_MANUAL, false))
                logger.info("Manual sync, skipping network checks")
            else {
                val syncConditions = syncConditionsFactory.create(accountSettings)

                // check internet connection
                if (!syncConditions.internetAvailable()) {
                    logger.info("WorkManager started SyncWorker without Internet connection. Aborting.")
                    return Result.success()
                }

                // check WiFi restriction
                if (!syncConditions.wifiConditionsMet()) {
                    logger.info("WiFi conditions not met. Won't run periodic sync.")
                    return Result.success()
                }
            }

            return doSyncWork(account, dataType)
        } finally {
            logger.info("${javaClass.simpleName} finished for $syncTag")
            runningSyncs -= syncTag

            if (Build.VERSION.SDK_INT >= 31 && stopReason != WorkInfo.STOP_REASON_NOT_STOPPED)
                logger.warning("Worker was stopped with reason: $stopReason")
        }
    }

    suspend fun doSyncWork(account: Account, dataType: SyncDataType): Result {
        logger.info("Running ${javaClass.name}: account=$account, dataType=$dataType")

        // pass supplied parameters to the selected syncer
        val resyncType: ResyncType? = when (inputData.getInt(INPUT_RESYNC, NO_RESYNC)) {
            RESYNC_ENTRIES -> ResyncType.RESYNC_ENTRIES
            RESYNC_LIST -> ResyncType.RESYNC_LIST
            else -> null
        }

        // Comes in through SyncAdapterService and is used only by ContactsSyncManager for an Android 7 workaround.
        val syncFrameworkUpload = inputData.getBoolean(INPUT_UPLOAD, false)

        // We still use the sync adapter framework's SyncResult to pass the sync results, but this
        // is only for legacy reasons and can be replaced by our own result class in the future.
        val syncResult = SyncResult()

        // What are we going to sync? Select syncer based on authority
        val syncer = when (dataType) {
            SyncDataType.CONTACTS ->
                addressBookSyncer.create(account, resyncType, syncFrameworkUpload, syncResult)
            SyncDataType.EVENTS ->
                calendarSyncer.create(account, resyncType, syncResult)
            SyncDataType.TASKS -> {
                val currentProvider = tasksAppManager.get().currentProvider()
                when (currentProvider) {
                    TaskProvider.ProviderName.JtxBoard ->
                        jtxSyncer.create(account, resyncType, syncResult)
                    TaskProvider.ProviderName.OpenTasks,
                    TaskProvider.ProviderName.TasksOrg ->
                        taskSyncer.create(account, currentProvider, resyncType, syncResult)
                    else -> {
                        logger.warning("No valid tasks provider found, aborting sync")
                        return Result.failure()
                    }
                }
            }
        }

        // Start syncing
        runInterruptible {
            syncer()
        }

        // convert SyncResult from Syncers to worker Data
        val output = Data.Builder()
            .putString("syncresult", syncResult.toString())

        // Check for errors
        if (syncResult.hasError()) {
            val softErrorNotificationTag = "${account.type}-${account.name}-$dataType"

            // On soft errors the sync is retried a few times before considered failed
            if (syncResult.hasSoftError()) {
                logger.log(Level.WARNING, "Soft error while syncing", syncResult)
                if (runAttemptCount < MAX_RUN_ATTEMPTS) {
                    val blockDuration = syncResult.delayUntil - System.currentTimeMillis() / 1000
                    logger.warning("Waiting for $blockDuration seconds, before retrying ...")

                    // We block the SyncWorker here so that it won't be started by the sync framework immediately again.
                    // This should be replaced by proper work scheduling as soon as we don't depend on the sync framework anymore.
                    if (blockDuration > 0)
                        delay(blockDuration * 1000)

                    logger.warning("Retrying on soft error (attempt $runAttemptCount of $MAX_RUN_ATTEMPTS)")
                    return Result.retry()
                }

                logger.warning("Max retries on soft errors reached ($runAttemptCount of $MAX_RUN_ATTEMPTS). Treating as failed")
                notificationRegistry.notifyIfPossible(NotificationRegistry.NOTIFY_SYNC_ERROR, tag = softErrorNotificationTag) {
                    NotificationCompat.Builder(applicationContext, notificationRegistry.CHANNEL_SYNC_IO_ERRORS)
                        .setSmallIcon(R.drawable.ic_sync_problem_notify)
                        .setContentTitle(account.name)
                        .setContentText(applicationContext.getString(R.string.sync_error_retry_limit_reached))
                        .setSubText(account.name)
                        .setOnlyAlertOnce(true)
                        .setPriority(NotificationCompat.PRIORITY_MIN)
                        .setCategory(NotificationCompat.CATEGORY_ERROR)
                        .build()
                }

                output.putBoolean(OUTPUT_TOO_MANY_RETRIES, true)
                return Result.failure(output.build())
            }

            // If no soft error found, dismiss sync error notification
            val notificationManager = NotificationManagerCompat.from(applicationContext)
            notificationManager.cancel(
                softErrorNotificationTag,
                NotificationRegistry.NOTIFY_SYNC_ERROR
            )

            // On a hard error - fail with an error message
            // Note: SyncManager should have notified the user
            if (syncResult.hasHardError()) {
                logger.log(Level.WARNING, "Hard error while syncing", syncResult)
                return Result.failure(output.build())
            }
        }

        logger.log(Level.INFO, "Sync worker succeeded", syncResult)
        return Result.success(output.build())
    }


    companion object {

        // common worker input parameters
        internal const val INPUT_ACCOUNT_NAME = "accountName"
        internal const val INPUT_ACCOUNT_TYPE = "accountType"
        internal const val INPUT_DATA_TYPE = "dataType"

        /** set to `true` for user-initiated sync that skips network checks */
        internal const val INPUT_MANUAL = "manual"

        /** set to `true` for syncs that are caused because the sync framework notified us about local changes */
        internal const val INPUT_UPLOAD = "upload"

        /** Whether re-synchronization is requested. One of [NO_RESYNC] (default), [RESYNC_LIST] or [RESYNC_ENTRIES]. */
        internal const val INPUT_RESYNC = "resync"
        @IntDef(NO_RESYNC, RESYNC_LIST, RESYNC_ENTRIES)
        annotation class InputResync
        internal const val NO_RESYNC = 0
        /** Re-synchronization is requested. See [ResyncType.RESYNC_LIST] for details. */
        internal const val RESYNC_LIST = 1
        /** Full re-synchronization is requested. See [ResyncType.RESYNC_ENTRIES] for details. */
        internal const val RESYNC_ENTRIES = 2

        const val OUTPUT_TOO_MANY_RETRIES = "tooManyRetries"

        /**
         * How often this work will be retried to run after soft (network) errors.
         */
        internal const val MAX_RUN_ATTEMPTS = 5

        /**
         * Set of currently running syncs, identified by their [commonTag].
         */
        private val runningSyncs = Collections.synchronizedSet(HashSet<String>())

        /**
         * This tag shall be added to every worker that is enqueued by a subclass.
         */
        fun commonTag(account: Account, dataType: SyncDataType): String =
            "sync-$dataType ${account.type}/${account.name}"

    }

}