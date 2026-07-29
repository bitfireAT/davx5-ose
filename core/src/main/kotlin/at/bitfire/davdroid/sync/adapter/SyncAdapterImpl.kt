/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.adapter

import android.accounts.Account
import android.accounts.AccountManager
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.SyncResult
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.work.WorkManager
import at.bitfire.davdroid.R
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.SyncConditions
import at.bitfire.davdroid.sync.SyncDataType
import at.bitfire.davdroid.sync.account.InvalidAccountException
import at.bitfire.davdroid.sync.worker.SyncWorkerManager
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.minutes

/**
 * Entry point for the Sync Adapter Framework. Handles incoming sync requests from the Sync Adapter Framework when
 *
 * - contacts/events/tasks have changed in the content provider (for instance because of an edit
 * in the calendar app),
 * - a sync has explicitly been requested by a third-party app (for instance the calendar app).
 *
 * This class only forwards such requests to a [at.bitfire.davdroid.sync.worker.OneTimeSyncWorker]
 * and does not run the sync itself.
 */
class SyncAdapterImpl @Inject constructor(
    private val accountSettingsFactory: AccountSettings.Factory,
    private val collectionRepository: DavCollectionRepository,
    private val serviceRepository: DavServiceRepository,
    @ApplicationContext context: Context,
    private val logger: Logger,
    private val syncConditionsFactory: SyncConditions.Factory,
    private val syncFrameworkIntegration: Lazy<SyncFrameworkIntegration>,
    private val syncWorkerManager: SyncWorkerManager
) : AbstractThreadedSyncAdapter(
    /* context = */ context,
    /* autoInitialize = */ true     // Sets isSyncable=1 when isSyncable=-1 and SYNC_EXTRAS_INITIALIZE is set.
    // Doesn't matter for us because we have android:isAlwaysSyncable="true" for all sync adapters.
), SyncAdapter {

    /**
     * Scope used to wait until the synchronization is finished. Will be cancelled when the sync framework
     * requests cancellation.
     */
    private val waitScope = CoroutineScope(EmptyCoroutineContext)

    override fun onPerformSync(
        accountOrAddressBookAccount: Account,
        extras: Bundle,
        authority: String,
        provider: ContentProviderClient,
        syncResult: SyncResult
    ) = runBlocking {   // blocking entry point
        // Make sure we always return normally (never throw) so that AbstractThreadedSyncAdapter's
        // SyncThread reaches its finally block and calls SyncContext.onFinished().
        try {
            performSync(accountOrAddressBookAccount, extras, authority)
        } catch (e: Throwable) {
            logger.log(Level.WARNING, "Sync adapter entry point failed", e)
        } finally {
            if (isAffectedByAlwaysPendingBug)
                clearPendingFlag(accountOrAddressBookAccount, authority)
        }
    }

    /**
     * Does the actual work of [onPerformSync]: resolves the account, checks sync conditions,
     * enqueues a [at.bitfire.davdroid.sync.worker.OneTimeSyncWorker] and waits for it to finish.
     *
     * @param accountOrAddressBookAccount the account (or address book account) to sync
     * @param extras SyncAdapter-specific parameters as passed to [onPerformSync]
     * @param authority the authority of this sync request
     */
    private suspend fun performSync(
        accountOrAddressBookAccount: Account,
        extras: Bundle,
        authority: String
    ) {
        // We have to pass this old SyncFramework extra for an Android 7 workaround
        val upload = extras.containsKey(ContentResolver.SYNC_EXTRAS_UPLOAD)
        logger.info("Sync request via sync framework for $accountOrAddressBookAccount $authority (upload=$upload)")

        // If we should sync an address book account - find the account storing the settings
        val account = getAccount(accountOrAddressBookAccount)
        if (account == null) {
            logger.warning("Address book account $accountOrAddressBookAccount doesn't have an associated collection")
            return
        }

        // Check sync conditions
        if (!checkSyncConditions(account))
            return

        logger.fine("Starting OneTimeSyncWorker for $account $authority and waiting for it")
        val workerName = syncWorkerManager.enqueueOneTime(
            account,
            dataType = SyncDataType.fromAuthority(authority),
            fromUpload = upload
        )

        // Wait until worker has finished
        waitForWorker(workerName)

        logger.info("Worker $workerName has finished, returning to sync framework")
    }

    /**
     * Resolves the account that should actually be used for syncing. If [accountOrAddressBookAccount]
     * is an address book account, looks up the collection and service it belongs to and returns the
     * account storing the settings for that service. Otherwise returns [accountOrAddressBookAccount] as-is.
     *
     * @return the resolved account, or `null` if an address book account doesn't have an associated collection
     */
    private fun getAccount(accountOrAddressBookAccount: Account): Account? =
        if (accountOrAddressBookAccount.type == context.getString(R.string.account_type_address_book))
            AccountManager.get(context)
                .getUserData(accountOrAddressBookAccount, LocalAddressBook.USER_DATA_COLLECTION_ID)
                ?.toLongOrNull()
                ?.let { collectionId ->
                    collectionRepository.get(collectionId)?.let { collection ->
                        serviceRepository.getBlocking(collection.serviceId)?.let { service ->
                            Account(service.accountName, context.getString(R.string.account_type))
                        }
                    }
                }
        else
            accountOrAddressBookAccount

    /**
     * Checks whether a sync framework initiated sync should actually run for [account].
     *
     * @return whether the sync conditions are met (`false` also when the account doesn't exist anymore)
     */
    private fun checkSyncConditions(account: Account): Boolean {
        val accountSettings = try {
            accountSettingsFactory.create(account)
        } catch (e: InvalidAccountException) {
            logger.log(Level.WARNING, "Account doesn't exist anymore", e)
            return false
        }
        val syncConditions = syncConditionsFactory.create(accountSettings)
        if (!syncConditions.wifiConditionsMet()) {
            logger.info("Sync conditions not met. Aborting sync framework initiated sync")
            return false
        }
        return true
    }

    /**
     * Suspends until the worker with the given [workerName] finishes or times out.
     *
     * Doesn't report the outcome anywhere: all error handling and retry logic is done by the
     * workers themselves, so this method only cares that the worker has finished, not how.
     *
     * @param workerName The unique name of the worker to wait for.
     */
    private suspend fun waitForWorker(workerName: String) {
        logger.fine("Waiting for worker: $workerName to finish")
        val workManager = WorkManager.getInstance(context)

        // look up whether there's an unfinished worker with the given name
        val worker = workManager.getWorkInfosForUniqueWork(workerName).await().firstOrNull {
            !it.state.isFinished
        } ?: return

        // wait for worker to finish
        try {
            // we don't need a separate thread to wait
            waitScope.async(Dispatchers.Unconfined) {
                withTimeout(10.minutes) {   // max wait timeout
                    workManager.getWorkInfoByIdFlow(worker.id)
                        .filterNotNull()
                        .first { it.state.isFinished }
                }
            }.await()
        } catch (_: CancellationException) {
            // waiting for work was cancelled, either by timeout or because the worker has finished
            logger.fine("Not waiting for $workerName anymore.")
        }
    }

    /** Addresses an Android issue: the sync framework doesn't reliably clear its "pending" flag
     * for this sync request on its own.
     *
     * This method explicitly clears the "pending flag" by cancelling the sync. */
    private fun clearPendingFlag(account: Account, authority: String) {
        syncFrameworkIntegration.get().cancelSync(account, authority)
    }

    override fun onSecurityException(account: Account, extras: Bundle, authority: String, syncResult: SyncResult) {
        logger.warning("Security exception for $account/$authority")
    }

    override fun onSyncCanceled() {
        // Note: this is also called in response to our own cancellation at the end of every sync.

        // We don't call super.onSyncCanceled() / interrupt the sync thread here: AbstractThreadedSyncAdapter
        // only calls SyncContext.onFinished() if the thread is not interrupted.

        waitScope.cancel()
    }

    override fun onSyncCanceled(thread: Thread) = onSyncCanceled()

    override fun getBinder(): IBinder = syncAdapterBinder

    companion object {

        /* Sync framework bug: ContentResolver.isSyncPending() can get stuck returning true forever
        after a sync, starting with Android 14: https://issuetracker.google.com/issues/320542002.
        Confirmed still present on Android 15/16. The issue doesn't seem to be deterministic, so it
        can't be reproduced with a behavior test easily. */
        private val isAffectedByAlwaysPendingBug = Build.VERSION.SDK_INT >= 34

    }

}