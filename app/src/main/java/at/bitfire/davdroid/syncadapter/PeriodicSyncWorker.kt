/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.Context
import android.provider.CalendarContract
import androidx.hilt.work.HiltWorker
import androidx.work.*
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.AccountSettings
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit


/**
 * Handles scheduled sync requests.
 *
 * Enqueues immediate [SyncWorker] syncs at the appropriate moment. This will prevent the actual
 * sync code from running twice simultaneously (for manual and scheduled sync).
 *
 * For each account there will be multiple dedicated workers running for each authority.
 */
@HiltWorker
class PeriodicSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    companion object {

        private const val WORKER_TAG = "periodic-sync"

        // Worker input parameters
        internal const val ARG_ACCOUNT_NAME = "accountName"
        internal const val ARG_ACCOUNT_TYPE = "accountType"
        internal const val ARG_AUTHORITY = "authority"

        /**
         * Name of this worker.
         * Used to distinguish between other work processes. A worker names are unique. There can
         * never be two running workers with the same name.
         */
        fun workerName(account: Account, authority: String): String =
            "$WORKER_TAG $authority ${account.type}/${account.name}"

        /**
         * Activate scheduled synchronization of an account with a specific authority.
         *
         * @param account    account to sync
         * @param authority  authority to sync (for instance: [CalendarContract.AUTHORITY]])
         * @param interval   interval between recurring syncs in seconds
         * @return operation object to check when and whether activation was successful
         */
        fun enable(context: Context, account: Account, authority: String, interval: Long, syncWifiOnly: Boolean): Operation {
            val arguments = Data.Builder()
                .putString(ARG_AUTHORITY, authority)
                .putString(ARG_ACCOUNT_NAME, account.name)
                .putString(ARG_ACCOUNT_TYPE, account.type)
                .build()
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (syncWifiOnly)
                        NetworkType.UNMETERED
                    else
                        NetworkType.CONNECTED
                ).build()
            val workRequest = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(interval, TimeUnit.SECONDS)
                .addTag(WORKER_TAG)
                .setInputData(arguments)
                .setConstraints(constraints)
                .build()
            return WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                workerName(account, authority),
                // if a periodic sync exists already, we want to update it with the new interval
                // and/or new required network type (applies on next iteration of periodic worker)
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        /**
         * Disables scheduled synchronization of an account for a specific authority.
         *
         * @param account     account to sync
         * @param authority   authority to sync (for instance: [CalendarContract.AUTHORITY]])
         * @return operation object to check process state of work cancellation
         */
        fun disable(context: Context, account: Account, authority: String): Operation =
            WorkManager.getInstance(context)
                .cancelUniqueWork(workerName(account, authority))

        /**
         * Finds out whether the [PeriodicSyncWorker] is currently enqueued or running
         *
         * @param account       account to check
         * @param authority     authority to check (for instance: [CalendarContract.AUTHORITY]])
         * @return boolean      whether the [PeriodicSyncWorker] is running or enqueued
         */
        fun isEnabled(context: Context, account: Account, authority: String): Boolean =
            WorkManager.getInstance(context)
                .getWorkInfos(
                    WorkQuery.Builder
                        .fromUniqueWorkNames(listOf(workerName(account, authority)))
                        .addStates(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING))
                        .build()
                ).get()
                .isNotEmpty()

    }

    override fun doWork(): Result {
        val account = Account(
            inputData.getString(ARG_ACCOUNT_NAME) ?: throw IllegalArgumentException("$ARG_ACCOUNT_NAME required"),
            inputData.getString(ARG_ACCOUNT_TYPE) ?: throw IllegalArgumentException("$ARG_ACCOUNT_TYPE required")
        )
        val authority = inputData.getString(ARG_AUTHORITY) ?: throw IllegalArgumentException("$ARG_AUTHORITY required")
        Logger.log.info("Running periodic sync worker: account=$account, authority=$authority")

        val accountSettings = AccountSettings(applicationContext, account)
        if (!SyncWorker.wifiConditionsMet(applicationContext, accountSettings)) {
            Logger.log.info("Sync conditions not met. Won't run sync.")
            return Result.failure()
        }

        // Just request immediate sync
        Logger.log.info("Requesting immediate sync")
        SyncWorker.enqueue(applicationContext, account, authority)
        return Result.success()
    }
}