/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.worker

import android.content.Context
import android.os.Build
import androidx.annotation.IntDef
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import at.bitfire.davdroid.IoCoroutineWorker
import at.bitfire.davdroid.R
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.DbAccountId
import at.bitfire.davdroid.accounts.LegacyAccount
import at.bitfire.davdroid.push.PushNotificationManager
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.settings.AccountSettingsFactory
import at.bitfire.davdroid.sync.AddressBookSyncer
import at.bitfire.davdroid.sync.CalendarSyncer
import at.bitfire.davdroid.sync.JtxSyncer
import at.bitfire.davdroid.sync.ResyncType
import at.bitfire.davdroid.sync.SyncConditions
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.sync.SyncResult
import at.bitfire.davdroid.sync.SyncSettings
import at.bitfire.davdroid.sync.SyncSettingsProvider
import at.bitfire.davdroid.sync.TaskSyncer
import at.bitfire.davdroid.sync.TasksAppManager
import at.bitfire.davdroid.sync.account.InvalidAccountException
import at.bitfire.davdroid.sync.worker.BaseSyncWorker.Companion.MAX_RUN_ATTEMPTS
import at.bitfire.davdroid.sync.worker.BaseSyncWorker.Companion.NO_RESYNC
import at.bitfire.davdroid.sync.worker.BaseSyncWorker.Companion.RESYNC_ENTRIES
import at.bitfire.davdroid.sync.worker.BaseSyncWorker.Companion.RESYNC_LIST
import at.bitfire.davdroid.sync.worker.BaseSyncWorker.Companion.commonTag
import at.bitfire.davdroid.ui.NotificationRegistry
import at.bitfire.synctools.storage.TaskProvider
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.Collections
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * Base class of the sync workers: takes the account and the data type to be synchronized from the input
 * data, selects the matching [at.bitfire.davdroid.sync.Syncer] and runs it.
 *
 * ## Results reported to WorkManager
 *
 * WorkManager derives the state it records for a work from the [Result] we return, so we map the outcome
 * of a sync run like this:
 *
 * - Sync ran without errors: [Result.success]
 * - Sync was skipped, because another worker is already syncing the same data or because the sync
 *   conditions are not met (anymore): [Result.success], too – skipping is not an error, the next sync run
 *   will do the work.
 * - Soft error (temporary problem, like a network error) with attempts left: [Result.retry], so that
 *   WorkManager runs the work again later (at most [MAX_RUN_ATTEMPTS] retries after the initial run).
 * - Soft error without attempts left: [Result.failure] and a notification, because we're giving up.
 * - Hard error (the user has to take action, for instance fix their credentials): [Result.failure].
 *   [at.bitfire.davdroid.sync.SyncManager] has already notified the user about the details.
 * - The account doesn't exist (anymore), or there's no usable tasks provider: [Result.failure].
 *
 * Note that [Result.failure] also fails work that has been appended to this work – one-time syncs are
 * enqueued with [androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE] – so a sync request that came in while
 * this sync was running is dropped and has to be requested again.
 *
 * ## Cancellation
 *
 * A canceled sync is not mapped to a [Result] at all: the [CancellationException] is passed on to
 * WorkManager, which then:
 *
 * - runs the work again if it has stopped the worker itself (constraints not met anymore, execution
 *   timeout, quota, …),
 * - leaves the work `CANCELLED` if the work has been canceled explicitly (like by
 *   [SyncWorkerManager.cancelAllWork]), or
 * - records the run as failed if something within the app has canceled the sync coroutine (periodic work
 *   is simply run again in the next interval).
 *
 * That's why no sync code may swallow a [CancellationException].
 */
abstract class BaseSyncWorker(
    context: Context,
    private val workerParams: WorkerParameters
) : IoCoroutineWorker(context, workerParams) {

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var accountSettingsFactory: AccountSettingsFactory

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
    lateinit var syncSettingsProvider: SyncSettingsProvider

    @Inject
    lateinit var tasksAppManager: Lazy<TasksAppManager>

    @Inject
    lateinit var taskSyncer: TaskSyncer.Factory

    override suspend fun doIoWork(): Result {
        val accountId = requireNotNull(inputData.getAccountId()) { "AccountId required" }

        val dataType = SyncDataType.valueOf(inputData.getString(INPUT_DATA_TYPE) ?: throw IllegalArgumentException("INPUT_SYNC_DATA_TYPE required"))

        val syncTag = commonTag(accountId, dataType)
        logger.info("${javaClass.simpleName} called for $syncTag")

        if (!runningSyncs.add(syncTag)) {
            logger.info("There's already another worker running for $syncTag, skipping")
            return Result.success()
        }

        // Dismiss any pending push notification
        pushNotificationManager.dismiss(accountId, dataType)

        try {
            val accountSettings = try {
                accountSettingsFactory.create(accountId)
            } catch (_: InvalidAccountException) {
                val workId = workerParams.id
                logger.warning("No valid account settings for account $accountId, cancelling worker $workId")

                // make sure no more workers are run for the invalid account
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

            val settings = syncSettingsProvider.create(accountSettings)
            return doSyncWork(accountId, dataType, settings)

        } catch (e: CancellationException) {
            // Not an error: log it (so that it can be told apart from a failed sync) and pass it on to WorkManager.
            logger.log(Level.INFO, "Sync was cancelled", e)
            throw e

        } finally {
            logger.info("${javaClass.simpleName} finished for $syncTag")
            runningSyncs -= syncTag

            if (Build.VERSION.SDK_INT >= 31 && stopReason != WorkInfo.STOP_REASON_NOT_STOPPED)
                logger.warning("Worker was stopped with reason: $stopReason")
        }
    }

    suspend fun doSyncWork(accountId: AccountId, dataType: SyncDataType, settings: SyncSettings): Result {
        logger.info("Running ${javaClass.name}: account=$accountId, dataType=$dataType")

        // pass supplied parameters to the selected syncer
        val resyncType: ResyncType? = when (inputData.getInt(INPUT_RESYNC, NO_RESYNC)) {
            RESYNC_ENTRIES -> ResyncType.RESYNC_ENTRIES
            RESYNC_LIST -> ResyncType.RESYNC_LIST
            else -> null
        }

        // Comes in through SyncAdapterService and is used only by ContactsSyncManager for an Android 7 workaround.
        val syncFrameworkUpload = inputData.getBoolean(INPUT_UPLOAD, false)

        val syncResult = SyncResult()

        // What are we going to sync? Select syncer based on authority
        when (dataType) {
            SyncDataType.CONTACTS ->
                addressBookSyncer.create(accountId, resyncType, syncFrameworkUpload, syncResult, settings)
            SyncDataType.EVENTS ->
                calendarSyncer.create(accountId, resyncType, syncResult, settings)
            SyncDataType.TASKS -> {
                when (val currentProvider = tasksAppManager.get().currentProvider()) {
                    TaskProvider.ProviderName.JtxBoard ->
                        jtxSyncer.create(accountId, resyncType, syncResult, settings)
                    TaskProvider.ProviderName.OpenTasks,
                    TaskProvider.ProviderName.TasksOrg ->
                        taskSyncer.create(accountId, currentProvider, resyncType, syncResult, settings)
                    else -> {
                        logger.warning("No valid tasks provider found, aborting sync")
                        return Result.failure()
                    }
                }
            }
        }.use { syncer ->
            // Start syncing
            syncer()
        }

        // convert SyncResult from Syncers to worker Data
        val output = Data.Builder()
            .putString("syncresult", syncResult.toString())

        // Check for errors
        if (syncResult.hasError) {
            val softErrorNotificationTag = "$accountId-$dataType"

            // On soft errors the sync is retried a few times before considered failed
            if (syncResult.softError) {
                logger.warning("Soft error while syncing: $syncResult")
                if (runAttemptCount < MAX_RUN_ATTEMPTS) {
                    val blockDuration = syncResult.delayUntil - System.currentTimeMillis() / 1000
                    logger.warning("Waiting for $blockDuration seconds, before retrying ...")

                    // We block the SyncWorker here so that it won't be started by the sync framework immediately again.
                    // This should be replaced by proper work scheduling as soon as we don't depend on the sync framework anymore.
                    if (blockDuration > 0)
                        delay(blockDuration.seconds)

                    logger.warning("Retrying on soft error (attempt $runAttemptCount of $MAX_RUN_ATTEMPTS)")
                    return Result.retry()
                }

                logger.warning("Max retries on soft errors reached ($runAttemptCount of $MAX_RUN_ATTEMPTS). Treating as failed")
                val accountName = accountRepository.getAccountName(accountId)
                notificationRegistry.notifyIfPossible(NotificationRegistry.NOTIFY_SYNC_ERROR, tag = softErrorNotificationTag) {
                    NotificationCompat.Builder(applicationContext, notificationRegistry.CHANNEL_SYNC_IO_ERRORS)
                        .setSmallIcon(R.drawable.ic_sync_problem_notify)
                        .setContentTitle(accountName)
                        .setContentText(applicationContext.getString(R.string.sync_error_retry_limit_reached))
                        .setSubText(accountName)
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
            if (syncResult.hardError) {
                logger.warning("Hard error while syncing: $syncResult")
                return Result.failure(output.build())
            }
        }

        logger.info("Sync worker succeeded: $syncResult")
        return Result.success(output.build())
    }


    companion object {

        // common worker input parameters
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
        fun commonTag(accountId: AccountId, dataType: SyncDataType): String {
            return when (accountId) {
                is LegacyAccount -> {
                    val account = accountId.androidAccount
                    "sync-$dataType ${account.type}/${account.name}"
                }
                is DbAccountId -> "sync-$dataType ${accountId.id}"
            }
        }

    }

}