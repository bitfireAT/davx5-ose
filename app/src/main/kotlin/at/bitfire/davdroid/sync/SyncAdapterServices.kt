/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.app.Service
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.os.Bundle
import android.os.IBinder
import android.provider.ContactsContract
import androidx.work.WorkManager
import at.bitfire.davdroid.InvalidAccountException
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.worker.OneTimeSyncWorker
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.logging.Level
import javax.inject.Inject
import javax.inject.Provider

abstract class SyncAdapterService: Service() {

    @Inject
    lateinit var syncAdapter: Provider<SyncAdapter>

    override fun onBind(intent: Intent?): IBinder {
        return syncAdapter.get().syncAdapterBinder
    }

    /**
     * Entry point for the sync adapter framework.
     *
     * Handles incoming sync requests from the sync adapter framework.
     *
     * Although we do not use the sync adapter for syncing anymore, we keep this sole
     * adapter to provide exported services, which allow android system components and calendar,
     * contacts or task apps to sync via DAVx5.
     */
    class SyncAdapter @Inject constructor(
        private val accountSettingsFactory: AccountSettings.Factory,
        @ApplicationContext context: Context,
        private val syncConditionsFactory: SyncConditions.Factory
    ): AbstractThreadedSyncAdapter(
        context,
        true    // isSyncable shouldn't be -1 because DAVx5 sets it to 0 or 1.
                           // However, if it is -1 by accident, set it to 1 to avoid endless sync loops.
    ) {

        /**
         * Completable [Boolean], which will be set to
         *
         * - `true` when the related sync worker has finished
         * - `false` when the sync framework has requested cancellation.
         *
         * In any case, the sync framework shouldn't be blocked anymore as soon as a
         * value is available.
         */
        private val finished = CompletableDeferred<Boolean>()

        override fun onPerformSync(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            // We seem to have to pass this old SyncFramework extra for an Android 7 workaround
            val upload = extras.containsKey(ContentResolver.SYNC_EXTRAS_UPLOAD)
            Logger.log.info("Sync request via sync framework for $account $authority (upload=$upload)")

            val accountSettings = try {
                accountSettingsFactory.forAccount(account)
            } catch (e: InvalidAccountException) {
                Logger.log.log(Level.WARNING, "Account doesn't exist anymore", e)
                return
            }

            val syncConditions = syncConditionsFactory.create(accountSettings)
            // Should we run the sync at all?
            if (!syncConditions.wifiConditionsMet()) {
                Logger.log.info("Sync conditions not met. Aborting sync framework initiated sync")
                return
            }

            /* Special case for contacts: because address books are separate accounts, changed contacts cause
            this method to be called with authority = ContactsContract.AUTHORITY. However the sync worker shall be run for the
            address book authority instead. */
            val workerAccount = accountSettings.account         // main account in case of an address book account
            val workerAuthority =
                if (authority == ContactsContract.AUTHORITY)
                    context.getString(R.string.address_books_authority)
                else
                    authority

            Logger.log.fine("Starting OneTimeSyncWorker for $workerAccount $workerAuthority and waiting for it")
            val workerName = OneTimeSyncWorker.enqueue(context, workerAccount, workerAuthority, upload = upload)

            // Because we are not allowed to observe worker state on a background thread, we can not
            // use it to block the sync adapter. Instead we check periodically whether the sync has
            // finished, putting the thread to sleep in between checks.
            val workManager = WorkManager.getInstance(context)

            try {
                runBlocking {
                    withTimeout(10 * 60 * 1000) {   // block max. 10 minutes
                        // wait for finished worker state
                        workManager.getWorkInfosForUniqueWorkFlow(workerName).collect { info ->
                            if (info.any { it.state.isFinished })
                                cancel(CancellationException("$workerName has finished"))
                        }
                    }
                }
            } catch (e: CancellationException) {
                // waiting for work was cancelled, either by timeout or because the worker has finished
                Logger.log.log(Level.FINE, "Not waiting for OneTimeSyncWorker anymore (this is not an error)", e)
            }

            Logger.log.fine("Returning to sync framework")
        }

        override fun onSecurityException(account: Account, extras: Bundle, authority: String, syncResult: SyncResult) {
            Logger.log.log(Level.WARNING, "Security exception for $account/$authority")
        }

        override fun onSyncCanceled() {
            Logger.log.info("Sync adapter requested cancellation – won't cancel sync, but also won't block sync framework anymore")

            // unblock sync framework
            finished.complete(false)
        }

        override fun onSyncCanceled(thread: Thread) = onSyncCanceled()

    }

}

// exported sync adapter services; we need a separate class for each authority

@AndroidEntryPoint
class CalendarsSyncAdapterService: SyncAdapterService()

@AndroidEntryPoint
class ContactsSyncAdapterService: SyncAdapterService()

@AndroidEntryPoint
class JtxSyncAdapterService: SyncAdapterService()

@AndroidEntryPoint
class OpenTasksSyncAdapterService: SyncAdapterService()

@AndroidEntryPoint
class TasksOrgSyncAdapterService: SyncAdapterService()