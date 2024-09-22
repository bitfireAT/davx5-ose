/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.sync.worker

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.provider.CalendarContract
import androidx.annotation.IntDef
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import at.bitfire.davdroid.R
import at.bitfire.davdroid.sync.SyncDispatcher
import at.bitfire.davdroid.sync.SyncUtils
import at.bitfire.davdroid.ui.NotificationRegistry
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

/**
 * One-time sync worker.
 *
 * Expedited: yes
 *
 * Long-running: no
 */
@HiltWorker
class OneTimeSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    syncDispatcher: SyncDispatcher
) : BaseSyncWorker(appContext, workerParams, syncDispatcher.dispatcher) {

    companion object {

        const val ARG_UPLOAD = "upload"

        const val ARG_RESYNC = "resync"
        @IntDef(NO_RESYNC, RESYNC, FULL_RESYNC)
        annotation class ArgResync
        const val NO_RESYNC = 0
        const val RESYNC = 1
        const val FULL_RESYNC = 2

        /**
         * Unique work name of this worker. Can also be used as tag.
         *
         * Mainly used to query [WorkManager] for work state (by unique work name or tag).
         *
         * @param account the account this worker is running for
         * @param authority the authority this worker is running for
         * @return Name of this worker composed as "onetime-sync $authority ${account.type}/${account.name}"
         */
        fun workerName(account: Account, authority: String): String =
            "onetime-sync $authority ${account.type}/${account.name}"

        /**
         * Requests immediate synchronization of an account with all applicable
         * authorities (contacts, calendars, …).
         *
         * @see enqueue
         */
        fun enqueueAllAuthorities(
            context: Context,
            account: Account,
            manual: Boolean = false,
            @ArgResync resync: Int = NO_RESYNC,
            upload: Boolean = false,
            isPush: Boolean = false
        ) {
            for (authority in SyncUtils.syncAuthorities(context))
                enqueue(context, account, authority, manual = manual, resync = resync, upload = upload, isPush = isPush)
        }

        /**
         * Requests immediate synchronization of an account with a specific authority.
         *
         * @param account       account to sync
         * @param authority     authority to sync (for instance: [CalendarContract.AUTHORITY])
         * @param manual        user-initiated sync (ignores network checks)
         * @param resync        whether to request (full) re-synchronization or not
         * @param upload        see [ContentResolver.SYNC_EXTRAS_UPLOAD] used only for contacts sync
         *                      and android 7 workaround
         * @param isPush        whether this sync is triggered by a push notification
         * @return existing or newly created worker name
         */
        fun enqueue(
            context: Context,
            account: Account,
            authority: String,
            manual: Boolean = false,
            @ArgResync resync: Int = NO_RESYNC,
            upload: Boolean = false,
            isPush: Boolean = false
        ): String {
            // Worker arguments
            val argumentsBuilder = Data.Builder()
                .putString(INPUT_AUTHORITY, authority)
                .putString(INPUT_ACCOUNT_NAME, account.name)
                .putString(INPUT_ACCOUNT_TYPE, account.type)
            if (manual)
                argumentsBuilder.putBoolean(INPUT_MANUAL, true)
            if (resync != NO_RESYNC)
                argumentsBuilder.putInt(ARG_RESYNC, resync)
            argumentsBuilder.putBoolean(ARG_UPLOAD, upload)

            // build work request
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)   // require a network connection
                .build()
            val workRequest = OneTimeWorkRequestBuilder<OneTimeSyncWorker>()
                .addTag(workerName(account, authority))
                .addTag(commonTag(account, authority))
                .setInputData(argumentsBuilder.build())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.DEFAULT_BACKOFF_DELAY_MILLIS,   // 30 sec
                    TimeUnit.MILLISECONDS
                )
                .setConstraints(constraints)

                /* OneTimeSyncWorker is started by user or sync framework when there are local changes.
                In both cases, synchronization should be done as soon as possible, so we set expedited. */
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)

            // enqueue and start syncing
            val name = workerName(account, authority)
            val request = workRequest.build()
            Logger.getGlobal().log(Level.INFO, "Enqueueing unique worker: $name, tags = ${request.tags}")
            WorkManager.getInstance(context).enqueueUniqueWork(
                name,
                /* If sync is already running, just continue.
                Existing retried work will not be replaced (for instance when
                PeriodicSyncWorker enqueues another scheduled sync). */
                ExistingWorkPolicy.KEEP,
                request
            )

            // Show notification if called by push
            if (isPush) {
                val notificationRegistry = NotificationRegistry(context, Logger.getLogger("OneTimeSyncWorker"))
                val id = account.name.hashCode() + account.type.hashCode() + authority.hashCode()

                notificationRegistry.notifyIfPossible(id) {
                    NotificationCompat.Builder(context, notificationRegistry.CHANNEL_STATUS)
                        .setSmallIcon(R.drawable.ic_sync)
                        .setContentTitle(context.getString(R.string.sync_notification_pending_push_title))
                        .setContentText(context.getString(R.string.sync_notification_pending_push_message))
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setCategory(NotificationCompat.CATEGORY_STATUS)
                        .setAutoCancel(true)
                        .build()
                }
            }

            return name
        }

    }


    /**
     * Used by WorkManager to show a foreground service notification for expedited jobs on Android <12.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, notificationRegistry.CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_foreground_notify)
            .setContentTitle(applicationContext.getString(R.string.foreground_service_notify_title))
            .setContentText(applicationContext.getString(R.string.foreground_service_notify_text))
            .setStyle(NotificationCompat.BigTextStyle())
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()
        return ForegroundInfo(NotificationRegistry.NOTIFY_SYNC_EXPEDITED, notification)
    }

}