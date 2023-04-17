/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.work.*
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.ui.NotificationUtils
import at.bitfire.davdroid.util.LiveDataUtils
import at.bitfire.davdroid.util.closeCompat
import at.bitfire.ical4android.TaskProvider
import com.google.common.util.concurrent.ListenableFuture
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.logging.Level

/**
 * Handles sync requests
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    companion object {

        const val ARG_ACCOUNT_NAME = "accountName"
        const val ARG_ACCOUNT_TYPE = "accountType"
        const val ARG_AUTHORITY = "authority"

        fun workerName(account: Account, authority: String): String {
            return "explicit-sync $authority ${account.type}/${account.name}"
        }

        /**
         * Requests immediate synchronization of an account with all applicable
         * authorities (contacts, calendars, …).
         *
         * @param account   account to sync
         */
        fun requestSync(context: Context, account: Account) {
            for (authority in SyncUtils.syncAuthorities(context))
                requestSync(context, account, authority)
        }

        /**
         * Requests immediate synchronization of an account with a specific authority.
         *
         * @param account     account to sync
         * @param authority   authority to sync (for instance: [CalendarContract.AUTHORITY]])
         */
        fun requestSync(context: Context, account: Account, authority: String) {
            val arguments = Data.Builder()
                .putString(ARG_AUTHORITY, authority)
                .putString(ARG_ACCOUNT_NAME, account.name)
                .putString(ARG_ACCOUNT_TYPE, account.type)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(arguments)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                workerName(account, authority),
                ExistingWorkPolicy.KEEP,    // if sync is already running, just continue
                workRequest
            )
        }

        fun stopSync(context: Context, account: Account, authority: String) {
            WorkManager.getInstance(context).cancelUniqueWork(workerName(account, authority))
        }

        /**
         * Will tell whether a worker exists, which belongs to given account and authorities,
         * and that is in the given worker state.
         *
         * @param workState    state of worker to match
         * @param account      the account which the workers belong to
         * @param authorities  type of sync work
         * @return boolean     *true* if at least one worker with matching state was found; *false* otherwise
         */
        fun existsForAccount(context: Context, workState: WorkInfo.State, account: Account, authorities: List<String>) =
            LiveDataUtils.liveDataLogicOr(
                authorities.map { authority -> isWorkerInState(context, workState, account, authority) }
            )

        fun isWorkerInState(context: Context, workState: WorkInfo.State, account: Account, authority: String) =
            Transformations.map(WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(workerName(account, authority))) { workInfoList ->
                workInfoList.any { workInfo -> workInfo.state == workState }
            }


        /**
         * Finds out whether SyncWorkers with given statuses exist
         *
         * @param statuses statuses to check
         * @return whether SyncWorkers matching the statuses were found
         */
        fun existsWithStatuses(context: Context, statuses: List<WorkInfo.State>): LiveData<Boolean> {
            val workQuery = WorkQuery.Builder
                .fromStates(statuses)
                .build()
            return Transformations.map(
                WorkManager.getInstance(context).getWorkInfosLiveData(workQuery)
            ) { it.isNotEmpty() }
        }

    }


    /** thread which runs the actual sync code (can be interrupted to stop synchronization)  */
    var syncThread: Thread? = null

    override fun doWork(): Result {
        val account = Account(
            inputData.getString(ARG_ACCOUNT_NAME) ?: throw IllegalArgumentException("$ARG_ACCOUNT_NAME required"),
            inputData.getString(ARG_ACCOUNT_TYPE) ?: throw IllegalArgumentException("$ARG_ACCOUNT_TYPE required")
        )
        val authority = inputData.getString(ARG_AUTHORITY) ?: throw IllegalArgumentException("$ARG_AUTHORITY required")
        Logger.log.info("Running sync worker: account=$account, authority=$authority")

        val syncAdapter = when (authority) {
            applicationContext.getString(R.string.address_books_authority) ->
                AddressBooksSyncAdapterService.AddressBooksSyncAdapter(applicationContext)
            CalendarContract.AUTHORITY ->
                CalendarsSyncAdapterService.CalendarsSyncAdapter(applicationContext)
            ContactsContract.AUTHORITY ->
                ContactsSyncAdapterService.ContactsSyncAdapter(applicationContext)
            TaskProvider.ProviderName.JtxBoard.authority ->
                JtxSyncAdapterService.JtxSyncAdapter(applicationContext)
            TaskProvider.ProviderName.OpenTasks.authority,
            TaskProvider.ProviderName.TasksOrg.authority ->
                TasksSyncAdapterService.TasksSyncAdapter(applicationContext)
            else ->
                throw IllegalArgumentException("Invalid authority $authority")
        }

        // Pass flags to the sync adapter. Note that these are sync framework flags, but they don't
        // have anything to do with the sync framework anymore. They only exist because we still use
        // the same sync code called from two locations (from the WorkManager and from the sync framework).
        val extras = Bundle(2)
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)        // manual sync
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)     // run immediately (don't queue)
        val result = SyncResult()

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

        try {
            syncThread = Thread.currentThread()
            syncAdapter.onPerformSync(account, extras, authority, provider, result)
        } catch (e: SecurityException) {
            syncAdapter.onSecurityException(account, extras, authority, result)
        } finally {
            provider.closeCompat()
        }

        if (result.hasError())
            return Result.failure(Data.Builder()
                .putString("syncresult", result.toString())
                .putString("syncResultStats", result.stats.toString())
                .build())

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