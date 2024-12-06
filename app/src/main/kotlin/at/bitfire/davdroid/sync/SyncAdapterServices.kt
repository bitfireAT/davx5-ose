/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.accounts.AccountManager
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
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.resource.LocalAddressBook.Companion.USER_DATA_COLLECTION_ID
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import javax.inject.Provider

abstract class SyncAdapterService: Service() {

    @Inject
    lateinit var syncAdapter: Provider<SyncAdapter>

    override fun onBind(intent: Intent?): IBinder {
        return syncAdapter.get().syncAdapterBinder
    }

    /**
     * Entry point for the Sync Adapter Framework.
     *
     * Handles incoming sync requests from the Sync Adapter Framework.
     *
     * Although we do not use the sync adapter for syncing anymore, we keep this sole
     * adapter to provide exported services, which allow android system components and calendar,
     * contacts or task apps to sync via DAVx5.
     *
     * All Sync Adapter Framework related interaction should happen inside [SyncFrameworkIntegration].
     */
    class SyncAdapter @Inject constructor(
        private val accountSettingsFactory: AccountSettings.Factory,
        private val collectionRepository: DavCollectionRepository,
        private val serviceRepository: DavServiceRepository,
        @ApplicationContext context: Context,
        private val logger: Logger,
        private val syncConditionsFactory: SyncConditions.Factory,
        private val syncWorkerManager: SyncWorkerManager
    ): AbstractThreadedSyncAdapter(
        context,
        true    // isSyncable shouldn't be -1 because DAVx5 (SyncFrameworkIntegration) sets it to 0 or 1.
                // However, if it is -1 by accident, set it to 1 to avoid endless sync loops.
    ) {

        /**
         * Scope used to wait until the synchronization is finished. Will be cancelled when the sync framework
         * requests cancellation.
         */
        private val waitScope = CoroutineScope(Dispatchers.Default)

        override fun onPerformSync(accountOrAddressBookAccount: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            // We have to pass this old SyncFramework extra for an Android 7 workaround
            val upload = extras.containsKey(ContentResolver.SYNC_EXTRAS_UPLOAD)
            logger.info("Sync request via sync framework for $accountOrAddressBookAccount $authority (upload=$upload)")

            // If we should sync an address book account - find the account storing the settings
            val account = if (accountOrAddressBookAccount.type == context.getString(R.string.account_type_address_book))
                AccountManager.get(context)
                    .getUserData(accountOrAddressBookAccount, USER_DATA_COLLECTION_ID)
                    ?.toLongOrNull()
                    ?.let { collectionId ->
                    collectionRepository.get(collectionId)?.let { collection ->
                        serviceRepository.get(collection.serviceId)?.let { service ->
                            Account(service.accountName, context.getString(R.string.account_type))
                        }
                    }
                }
            else
                accountOrAddressBookAccount

            if (account == null) {
                logger.warning("Address book account $accountOrAddressBookAccount doesn't have an associated collection")
                return
            }

            val accountSettings = try {
                accountSettingsFactory.create(account)
            } catch (e: InvalidAccountException) {
                logger.log(Level.WARNING, "Account doesn't exist anymore", e)
                return
            }

            val syncConditions = syncConditionsFactory.create(accountSettings)
            // Should we run the sync at all?
            if (!syncConditions.wifiConditionsMet()) {
                logger.info("Sync conditions not met. Aborting sync framework initiated sync")
                return
            }

            /* Special case for contacts: because address books are separate accounts, changed contacts cause
            this method to be called with authority = ContactsContract.AUTHORITY. However the sync worker shall be run for the
            address book authority instead. */
            val workerAuthority =
                if (authority == ContactsContract.AUTHORITY)
                    context.getString(R.string.address_books_authority)
                else
                    authority

            val dataType = SyncDataType.fromAuthority(context, workerAuthority)
            logger.fine("Starting OneTimeSyncWorker for $account $dataType (from $workerAuthority) and waiting for it")
            val workerName = syncWorkerManager.enqueueOneTime(account = account, dataType = dataType, upload = upload)

            /* Because we are not allowed to observe worker state on a background thread, we can not
            use it to block the sync adapter. Instead we use a Flow to get notified when the sync
            has finished. */
            val workManager = WorkManager.getInstance(context)

            try {
                val waitJob = waitScope.launch {
                    // wait for finished worker state
                    workManager.getWorkInfosForUniqueWorkFlow(workerName).collect { info ->
                        if (info.any { it.state.isFinished })
                            cancel("$workerName has finished")
                    }
                }

                runBlocking {
                    withTimeout(10 * 60 * 1000) {   // block max. 10 minutes
                        waitJob.join()              // wait until worker has finished
                    }
                }
            } catch (_: CancellationException) {
                // waiting for work was cancelled, either by timeout or because the worker has finished
                logger.fine("Not waiting for OneTimeSyncWorker anymore.")
            }

            logger.info("Returning to sync framework.")
        }

        override fun onSecurityException(account: Account, extras: Bundle, authority: String, syncResult: SyncResult) {
            logger.log(Level.WARNING, "Security exception for $account/$authority")
        }

        override fun onSyncCanceled() {
            logger.info("Sync adapter requested cancellation – won't cancel sync, but also won't block sync framework anymore")

            // unblock sync framework
            waitScope.cancel()
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