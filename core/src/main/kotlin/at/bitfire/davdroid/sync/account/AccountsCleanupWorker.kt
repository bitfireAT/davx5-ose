/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.account

import android.accounts.AccountManager
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.hilt.work.HiltWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.repository.AccountRepository
import at.bitfire.davdroid.resource.local.AddressBookAccountProperties
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.logging.Logger

@HiltWorker
class AccountsCleanupWorker @AssistedInject constructor(
    @Assisted val context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val accountManager: AccountManager,
    private val accountRepository: AccountRepository,
    private val addressBookAccountProperties: AddressBookAccountProperties,
    private val db: AppDatabase,
    private val logger: Logger
): Worker(context, workerParameters) {

    @AssistedFactory
    @VisibleForTesting
    interface Factory {
        fun create(appContext: Context, workerParams: WorkerParameters): AccountsCleanupWorker
    }

    override fun doWork(): Result {
        lockAccountsCleanup()
        try {
            cleanUpServices()
            cleanUpAddressBooks()
        } finally {
            unlockAccountsCleanup()
        }
        return Result.success()
    }

    /**
     * Deletes services in the database which are not associated to a valid account.
     */
    @VisibleForTesting
    internal fun cleanUpServices() {
        // Later, accounts which are not in the DB should be deleted here

        // Delete orphaned services in DB – only necessary as long as accounts are implemented as system accounts (not in DB)
        val accountIds = accountRepository.getAllBlocking()
        logger.info("Cleaning up accounts. Currently existing accounts: ${accountIds.joinToString()}")
        val serviceDao = db.serviceDao()
        if (accountIds.isEmpty()) {
            serviceDao.deleteAllBlocking()
        } else {
            val accountNames = accountIds.map { accountId -> accountRepository.getAccountNameBlocking(accountId) }
            serviceDao.deleteExceptAccountsBlocking(accountNames)
        }
    }

    /**
     * Deletes address book accounts which are not assigned to a valid account.
     */
    @VisibleForTesting
    internal fun cleanUpAddressBooks() {
        val accountIds = accountRepository.getAllBlocking().toSet()
        for (addressBookAccount in accountManager.getAccountsByType(context.getString(R.string.account_type_address_book))) {
            val accountId = addressBookAccountProperties.getAppAccount(addressBookAccount)
            if (accountId !in accountIds) {
                // If no valid account exists for this address book, we can delete it
                logger.info("Deleting address book account without valid account: $addressBookAccount")
                accountManager.removeAccountExplicitly(addressBookAccount)
            }
        }
    }


    companion object {

        const val NAME = "accounts-cleanup"

        private val mutex = Semaphore(1)
        /**
         * Prevents account cleanup from being run until `unlockAccountsCleanup` is called.
         * Can only be active once at the same time globally (blocking).
         */
        fun lockAccountsCleanup() = mutex.acquire()
        /** Must be called exactly one time after calling `lockAccountsCleanup`. */
        fun unlockAccountsCleanup() = mutex.release()

        /**
         * Enqueues [AccountsCleanupWorker] to be run once as soon as possible.
         */
        fun enqueue(context: Context) {
            // run once
            val rq = OneTimeWorkRequestBuilder<AccountsCleanupWorker>()
            WorkManager.getInstance(context).enqueue(rq.build())
        }

        /**
         * Enqueues [AccountsCleanupWorker] to be run regularly (but not necessarily now).
         *
         * Non-blocking ([WorkManager.enqueueUniquePeriodicWork]).
         */
        fun enable(context: Context) {
            // run every day
            val rq = PeriodicWorkRequestBuilder<AccountsCleanupWorker>(Duration.ofDays(1))
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, rq.build())
        }

    }

}