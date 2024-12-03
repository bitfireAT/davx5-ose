/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.sync.worker

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.provider.CalendarContract
import androidx.annotation.IntDef
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.WorkerParameters
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.push.PushNotificationManager
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.AddressBookSyncer
import at.bitfire.davdroid.sync.CalendarSyncer
import at.bitfire.davdroid.sync.JtxSyncer
import at.bitfire.davdroid.sync.SyncConditions
import at.bitfire.davdroid.sync.SyncResult
import at.bitfire.davdroid.sync.Syncer
import at.bitfire.davdroid.sync.TaskSyncer
import at.bitfire.davdroid.ui.NotificationRegistry
import at.bitfire.ical4android.TaskProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.logging.Logger
import javax.inject.Inject

abstract class BaseSyncWorker(
    context: Context,
    private val workerParams: WorkerParameters,
    private val syncDispatcher: CoroutineDispatcher
) : CoroutineWorker(context, workerParams) {

    companion object {

        // common worker input parameters
        const val INPUT_ACCOUNT_NAME = "accountName"
        const val INPUT_ACCOUNT_TYPE = "accountType"
        const val INPUT_AUTHORITY = "authority"

        /** set to `true` for user-initiated sync that skips network checks */
        const val INPUT_MANUAL = "manual"

        /** set to `true` for syncs that are caused by local changes */
        const val INPUT_UPLOAD = "upload"

        /** Whether re-synchronization is requested. One of [NO_RESYNC] (default), [RESYNC] or [FULL_RESYNC]. */
        const val INPUT_RESYNC = "resync"
        @IntDef(NO_RESYNC, RESYNC, FULL_RESYNC)
        annotation class InputResync
        const val NO_RESYNC = 0
        /** Re-synchronization is requested. See [Syncer.SYNC_EXTRAS_RESYNC] for details. */
        const val RESYNC = 1
        /** Full re-synchronization is requested. See [Syncer.SYNC_EXTRAS_FULL_RESYNC] for details. */
        const val FULL_RESYNC = 2

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
         * This tag shall be added to every worker that is enqueued by a subclass.
         */
        fun commonTag(account: Account, authority: String): String =
            "sync-$authority ${account.type}/${account.name}"

        /**
         * Observes whether >0 sync workers (both [PeriodicSyncWorker] and [OneTimeSyncWorker])
         * exist, belonging to given account and authorities, and which are/is in the given worker state.
         *
         * @param workStates   list of states of workers to match
         * @param account      the account which the workers belong to
         * @param authorities  type of sync work, ie [CalendarContract.AUTHORITY]
         * @param whichTag     function to generate tag that should be observed for given account and authority
         *
         * @return flow that emits `true` if at least one worker with matching query was found; `false` otherwise
         */
        fun exists(
            context: Context,
            workStates: List<WorkInfo.State>,
            account: Account? = null,
            authorities: List<String>? = null,
            whichTag: (account: Account, authority: String) -> String = { account, authority ->
                commonTag(account, authority)
            }
        ): Flow<Boolean> {
            val workQuery = WorkQuery.Builder.fromStates(workStates)
            if (account != null && authorities != null)
                workQuery.addTags(
                    authorities.map { authority -> whichTag(account, authority) }
                )
            return WorkManager.getInstance(context)
                .getWorkInfosFlow(workQuery.build())
                .map { workInfoList ->
                    workInfoList.isNotEmpty()
                }
        }

    }

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
    lateinit var taskSyncer: TaskSyncer.Factory


    override suspend fun doWork(): Result {
        // ensure we got the required arguments
        val account = Account(
            inputData.getString(INPUT_ACCOUNT_NAME) ?: throw IllegalArgumentException("$INPUT_ACCOUNT_NAME required"),
            inputData.getString(INPUT_ACCOUNT_TYPE) ?: throw IllegalArgumentException("$INPUT_ACCOUNT_TYPE required")
        )
        val authority = inputData.getString(INPUT_AUTHORITY) ?: throw IllegalArgumentException("$INPUT_AUTHORITY required")

        val syncTag = commonTag(account, authority)
        logger.info("${javaClass.simpleName} called for $syncTag")

        if (!runningSyncs.add(syncTag)) {
            logger.info("There's already another worker running for $syncTag, skipping")
            return Result.success()
        }

        // Dismiss any pending push notification
        pushNotificationManager.dismiss(account, authority)

        try {
            val accountSettings = try {
                accountSettingsFactory.create(account)
            } catch (e: InvalidAccountException) {
                val workId = workerParams.id
                logger.warning("Account $account doesn't exist anymore, cancelling worker $workId")

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

            return doSyncWork(account, authority, accountSettings)
        } finally {
            logger.info("${javaClass.simpleName} finished for $syncTag")
            runningSyncs -= syncTag

            if (Build.VERSION.SDK_INT >= 31 && stopReason != WorkInfo.STOP_REASON_NOT_STOPPED)
                logger.warning("Worker was stopped with reason: $stopReason")
        }
    }

    open suspend fun doSyncWork(
        account: Account,
        authority: String,
        accountSettings: AccountSettings
    ): Result = withContext(syncDispatcher) {
        logger.info("Running ${javaClass.name}: account=$account, authority=$authority")

        // pass possibly supplied flags to the selected syncer
        val extrasList = mutableListOf<String>()
        when (inputData.getInt(INPUT_RESYNC, NO_RESYNC)) {
            RESYNC ->      extrasList.add(Syncer.SYNC_EXTRAS_RESYNC)
            FULL_RESYNC -> extrasList.add(Syncer.SYNC_EXTRAS_FULL_RESYNC)
        }
        if (inputData.getBoolean(INPUT_UPLOAD, false))
            // Comes in through SyncAdapterService and is used only by ContactsSyncManager for an Android 7 workaround.
            extrasList.add(ContentResolver.SYNC_EXTRAS_UPLOAD)
        val extras = extrasList.toTypedArray()

        // We still use the sync adapter framework's SyncResult to pass the sync results, but this
        // is only for legacy reasons and can be replaced by our own result class in the future.
        val result = SyncResult()

        // What are we going to sync? Select syncer based on authority
        val syncer = when (authority) {
            applicationContext.getString(R.string.address_books_authority) ->
                addressBookSyncer.create(account, extras, result)
            CalendarContract.AUTHORITY ->
                calendarSyncer.create(account, extras, result)
            TaskProvider.ProviderName.JtxBoard.authority ->
                jtxSyncer.create(account, extras, result)
            TaskProvider.ProviderName.OpenTasks.authority,
            TaskProvider.ProviderName.TasksOrg.authority ->
                taskSyncer.create(account, authority, extras, result)
            else ->
                throw IllegalArgumentException("Invalid authority $authority")
        }

        // Start syncing
        runInterruptible {
            syncer()
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
                logger.warning("Soft error while syncing: result=$result, stats=${result.stats}")
                if (runAttemptCount < MAX_RUN_ATTEMPTS) {
                    val blockDuration = result.delayUntil - System.currentTimeMillis() / 1000
                    logger.warning("Waiting for $blockDuration seconds, before retrying ...")

                    // We block the SyncWorker here so that it won't be started by the sync framework immediately again.
                    // This should be replaced by proper work scheduling as soon as we don't depend on the sync framework anymore.
                    if (blockDuration > 0)
                        delay(blockDuration * 1000)

                    logger.warning("Retrying on soft error (attempt $runAttemptCount of $MAX_RUN_ATTEMPTS)")
                    return@withContext Result.retry()
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

                return@withContext Result.failure(syncResult)
            }

            // If no soft error found, dismiss sync error notification
            val notificationManager = NotificationManagerCompat.from(applicationContext)
            notificationManager.cancel(
                softErrorNotificationTag,
                NotificationRegistry.NOTIFY_SYNC_ERROR
            )

            // On a hard error - fail with an error message
            // Note: SyncManager should have notified the user
            if (result.hasHardError()) {
                logger.warning("Hard error while syncing: result=$result, stats=${result.stats}")
                return@withContext Result.failure(syncResult)
            }
        }

        return@withContext Result.success()
    }

}