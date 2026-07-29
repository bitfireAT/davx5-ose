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
import androidx.annotation.VisibleForTesting
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.time.Duration
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
     * The [Job] of the currently running sync (that waits for the worker to be finished).
     * Used to cancel the job when [onSyncCanceled] is called.
     *
     * Volatile because [onSyncCanceled] is called from a different thread than [onPerformSync].
     */
    @Volatile
    private var syncJob: Job? = null

    /**
     * Max time to wait for the worker in [waitForWorker]. Overridable for tests.
     */
    @VisibleForTesting
    internal var workerWaitTimeout: Duration = 10.minutes

    override fun onPerformSync(
        accountOrAddressBookAccount: Account,
        extras: Bundle,
        authority: String,
        provider: ContentProviderClient,
        syncResult: SyncResult
    ) = runBlocking {
        // remember Job of runBlocking's root coroutine to allow cancellation
        syncJob = coroutineContext.job

        try {
            performSync(accountOrAddressBookAccount, extras, authority)
        } catch (e: Throwable) {
            // catch and ignore any error so that the sync always finishes "successfully"
            logger.log(Level.WARNING, "onPerformSync error", e)
        } finally {
            // no need for cancellation anymore, clear job variable
            syncJob = null

            if (hasAlwaysPendingIssue)
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
    @VisibleForTesting
    internal suspend fun performSync(
        accountOrAddressBookAccount: Account,
        extras: Bundle,
        authority: String
    ) {
        // We have to pass this old SyncFramework extra for an Android 7 workaround
        val upload = extras.containsKey(ContentResolver.SYNC_EXTRAS_UPLOAD)
        logger.info("Sync request via sync framework for $accountOrAddressBookAccount $authority (upload=$upload)")

        // If we should sync an address book account: find the main account
        val account = getAccount(accountOrAddressBookAccount) ?: return

        // Check sync conditions (don't enqueue worker if sync conditions are not met)
        if (!checkSyncConditions(account))
            return

        logger.info("Starting OneTimeSyncWorker for $account $authority and waiting for it")
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
     * account storing the settings for that service. Otherwise, [accountOrAddressBookAccount] is directly returned.
     *
     * @return the resolved account, or `null` if an address book account doesn't have an associated collection
     */
    private suspend fun getAccount(accountOrAddressBookAccount: Account): Account? =
        if (accountOrAddressBookAccount.type == context.getString(R.string.account_type_address_book))
            AccountManager.get(context)
                .getUserData(accountOrAddressBookAccount, LocalAddressBook.USER_DATA_COLLECTION_ID)
                ?.toLongOrNull()
                ?.let { collectionId ->
                    collectionRepository.getAsync(collectionId)?.let { collection ->
                        serviceRepository.get(collection.serviceId)?.let { service ->
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
            logger.info("Sync conditions not met. Ignoring sync framework-initiated sync")
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
        val unfinishedWorker = workManager.getWorkInfosForUniqueWork(workerName).await().firstOrNull {
            !it.state.isFinished
        } ?: return

        // wait for worker to finish
        try {
            // we don't need a separate thread to wait
            withTimeout(workerWaitTimeout) {
                workManager.getWorkInfoByIdFlow(unfinishedWorker.id)
                    .filterNotNull()
                    .first { it.state.isFinished }  // collect flow until worker is finished
            }
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
        /* Note: this is also called in response to our own cancellation at the end of every sync
        (see clearPendingFlag/cancelSync), in which case syncJob is already null and there's
        nothing to cancel.

        We don't call super.onSyncCanceled() / interrupt the sync thread here: AbstractThreadedSyncAdapter
        only calls SyncContext.onFinished() if the thread is not interrupted. */

        syncJob?.cancel()
    }

    override fun onSyncCanceled(thread: Thread) = onSyncCanceled()

    override fun getBinder(): IBinder = syncAdapterBinder

    companion object {

        /* Sync framework bug: ContentResolver.isSyncPending() can get stuck returning true forever
        after a sync, starting with Android 14: https://issuetracker.google.com/issues/320542002. The
        issue doesn't seem to be deterministic, so it can't be reproduced with a behavior test easily. */
        private val hasAlwaysPendingIssue = Build.VERSION.SDK_INT >= 34

    }

}