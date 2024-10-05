/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.worker

import android.accounts.Account
import android.content.ContentResolver
import android.content.Context
import android.provider.CalendarContract
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import at.bitfire.davdroid.sync.SyncUtils
import at.bitfire.davdroid.sync.worker.BaseSyncWorker.Companion.INPUT_ACCOUNT_NAME
import at.bitfire.davdroid.sync.worker.BaseSyncWorker.Companion.INPUT_ACCOUNT_TYPE
import at.bitfire.davdroid.sync.worker.BaseSyncWorker.Companion.INPUT_AUTHORITY
import at.bitfire.davdroid.sync.worker.BaseSyncWorker.Companion.INPUT_MANUAL
import at.bitfire.davdroid.sync.worker.BaseSyncWorker.Companion.commonTag
import at.bitfire.davdroid.sync.worker.OneTimeSyncWorker.Companion.ARG_RESYNC
import at.bitfire.davdroid.sync.worker.OneTimeSyncWorker.Companion.ARG_UPLOAD
import at.bitfire.davdroid.sync.worker.OneTimeSyncWorker.Companion.ArgResync
import at.bitfire.davdroid.sync.worker.OneTimeSyncWorker.Companion.NO_RESYNC
import at.bitfire.davdroid.sync.worker.OneTimeSyncWorker.Companion.workerName
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

/**
 * For building and managing synchronization workers (both one-time and periodic).
 *
 * One-time sync workers can be enqueued. Periodic sync workers can be enabled and disabled.
 */
class SyncWorkerManager @Inject constructor(
    @ApplicationContext val context: Context
) {

    // one-time sync workers

    /**
     * Builds a one-time sync worker for a specific account and authority.
     *
     * Arguments: see [enqueueOneTime]
     *
     * @return one-time sync work request for the given arguments
     */
    fun buildOneTime(
        account: Account,
        authority: String,
        manual: Boolean = false,
        @ArgResync resync: Int = NO_RESYNC,
        upload: Boolean = false
    ): OneTimeWorkRequest {
        // worker arguments
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
        return OneTimeWorkRequestBuilder<OneTimeSyncWorker>()
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

            // build work request
            .build()
    }

    /**
     * Requests immediate synchronization of an account with a specific authority.
     *
     * @param account       account to sync
     * @param authority     authority to sync (for instance: [CalendarContract.AUTHORITY])
     * @param manual        user-initiated sync (ignores network checks)
     * @param resync        whether to request (full) re-synchronization or not
     * @param upload        see [ContentResolver.SYNC_EXTRAS_UPLOAD] – only used for contacts sync and Android 7 workaround
     *
     * @return existing or newly created worker name
     */
    fun enqueueOneTime(
        account: Account,
        authority: String,
        manual: Boolean = false,
        @ArgResync resync: Int = NO_RESYNC,
        upload: Boolean = false
    ): String {
        // enqueue and start syncing
        val name = workerName(account, authority)
        val request = buildOneTime(
            account = account,
            authority = authority,
            manual = manual,
            resync = resync,
            upload = upload
        )
        Logger.getGlobal().log(Level.INFO, "Enqueueing unique worker: $name, tags = ${request.tags}")
        WorkManager.getInstance(context).enqueueUniqueWork(
            name,
            /* If sync is already running, just continue.
            Existing retried work will not be replaced (for instance when
            PeriodicSyncWorker enqueues another scheduled sync). */
            ExistingWorkPolicy.KEEP,
            request
        )
        return name
    }

    /**
     * Requests immediate synchronization of an account with all applicable
     * authorities (contacts, calendars, …).
     *
     * Arguments: see [enqueueOneTime]
     */
    fun enqueueOneTimeAllAuthorities(
        account: Account,
        manual: Boolean = false,
        @ArgResync resync: Int = NO_RESYNC,
        upload: Boolean = false
    ) {
        for (authority in SyncUtils.syncAuthorities(context))
            enqueueOneTime(
                account = account,
                authority = authority,
                manual = manual,
                resync = resync,
                upload = upload
            )
    }


    // periodic sync workers

    /**
     * Builds a periodic sync worker for a specific account and authority.
     *
     * Arguments: see [enablePeriodic]
     *
     * @return periodic sync work request for the given arguments
     */
    fun buildPeriodic(account: Account, authority: String, interval: Long, syncWifiOnly: Boolean): PeriodicWorkRequest {
        val arguments = Data.Builder()
            .putString(INPUT_AUTHORITY, authority)
            .putString(INPUT_ACCOUNT_NAME, account.name)
            .putString(INPUT_ACCOUNT_TYPE, account.type)
            .build()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (syncWifiOnly)
                    NetworkType.UNMETERED
                else
                    NetworkType.CONNECTED
            ).build()
        return PeriodicWorkRequestBuilder<PeriodicSyncWorker>(interval, TimeUnit.SECONDS)
            .addTag(PeriodicSyncWorker.Companion.workerName(account, authority))
            .addTag(commonTag(account, authority))
            .setInputData(arguments)
            .setConstraints(constraints)
            .build()
    }

    /**
     * Activate periodic synchronization of an account with a specific authority.
     *
     * @param account    account to sync
     * @param authority  authority to sync (for instance: [CalendarContract.AUTHORITY]])
     * @param interval   interval between recurring syncs in seconds
     * @return operation object to check when and whether activation was successful
     */
    fun enablePeriodic(account: Account, authority: String, interval: Long, syncWifiOnly: Boolean): Operation {
        val workRequest = buildPeriodic(account, authority, interval, syncWifiOnly)
        return WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PeriodicSyncWorker.Companion.workerName(account, authority),
            // if a periodic sync exists already, we want to update it with the new interval
            // and/or new required network type (applies on next iteration of periodic worker)
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    /**
     * Disables periodic synchronization of an account for a specific authority.
     *
     * @param account     account to sync
     * @param authority   authority to sync (for instance: [CalendarContract.AUTHORITY]])
     * @return operation object to check process state of work cancellation
     */
    fun disablePeriodic(account: Account, authority: String): Operation =
        WorkManager.getInstance(context)
            .cancelUniqueWork(PeriodicSyncWorker.Companion.workerName(account, authority))

}