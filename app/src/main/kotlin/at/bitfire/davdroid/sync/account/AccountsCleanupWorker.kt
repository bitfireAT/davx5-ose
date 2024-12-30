/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

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
import at.bitfire.davdroid.resource.LocalAddressBook
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
    private val accountRepository: AccountRepository,
    private val db: AppDatabase,
    private val logger: Logger
): Worker(context, workerParameters) {

    @AssistedFactory
    @VisibleForTesting
    interface Factory {
        fun create(appContext: Context, workerParams: WorkerParameters): AccountsCleanupWorker
    }

    private val accountManager = AccountManager.get(context)

    override fun doWork(): Result {
        lockAccountsCleanup()
        try {
            accountRepository.removeOrphanedInDb()
            cleanUpAddressBooks()
        } finally {
            unlockAccountsCleanup()
        }
        return Result.success()
    }

    /**
     * Deletes address book accounts which are not assigned to a valid account.
     */
    @VisibleForTesting
    internal fun cleanUpAddressBooks() {
        val accounts = accountRepository.getAll()
        for (addressBookAccount in accountManager.getAccountsByType(context.getString(R.string.account_type_address_book))) {
            val accountName = accountManager.getUserData(addressBookAccount, LocalAddressBook.USER_DATA_ACCOUNT_NAME)
            val accountType = accountManager.getUserData(addressBookAccount, LocalAddressBook.USER_DATA_ACCOUNT_TYPE)
            if (!accounts.any { it.name == accountName && it.type == accountType }) {
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
         */
        fun enable(context: Context) {
            // run every day
            val rq = PeriodicWorkRequestBuilder<AccountsCleanupWorker>(Duration.ofDays(1))
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, rq.build())
        }

    }

}