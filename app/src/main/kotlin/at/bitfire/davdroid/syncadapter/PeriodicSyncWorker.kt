/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.Context
import android.provider.CalendarContract
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit


/**
 * Handles scheduled sync requests.
 *
 * The different periodic sync workers each carry a unique work name composed of the account and
 * authority which they are responsible for. For each account there will be multiple dedicated periodic
 * sync workers for each authority. See [PeriodicSyncWorker.workerName] for more information.
 *
 * Deferrable: yes (periodic)
 *
 * Expedited: no (→ no [getForegroundInfo])
 *
 * Long-running: no
 */
@HiltWorker
class PeriodicSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : BaseSyncWorker(appContext, workerParams) {

    companion object {

        /**
         * Unique work name of this worker. Can also be used as tag.
         *
         * Mainly used to query [WorkManager] for work state (by unique work name or tag).
         *
         * @param account the account this worker is running for
         * @param authority the authority this worker is running for
         * @return Name of this worker composed as "periodic-sync $authority ${account.type}/${account.name}"
         */
        fun workerName(account: Account, authority: String): String =
            "periodic-sync $authority ${account.type}/${account.name}"

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
            val workRequest = PeriodicWorkRequestBuilder<PeriodicSyncWorker>(interval, TimeUnit.SECONDS)
                .addTag(workerName(account, authority))
                .addTag(commonTag(account, authority))
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

    }

}