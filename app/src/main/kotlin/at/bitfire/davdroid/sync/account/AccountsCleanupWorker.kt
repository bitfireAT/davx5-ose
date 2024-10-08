/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.sync.account

import android.accounts.Account
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
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.resource.LocalAddressBook.Companion.USER_DATA_COLLECTION_ID
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.logging.Level
import java.util.logging.Logger

@HiltWorker
class AccountsCleanupWorker @AssistedInject constructor(
    @Assisted val context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val accountRepository: AccountRepository,
    private val collectionRepository: DavCollectionRepository,
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
            cleanupAccounts()
        } finally {
            unlockAccountsCleanup()
        }
        return Result.success()
    }

    private fun cleanupAccounts() {

        // Later, accounts which are not in the DB should be deleted here

        // delete orphaned services in DB
        val accounts = accountRepository.getAll()
        logger.log(Level.INFO, "Cleaning up accounts. Currently existing accounts:", accounts)
        val accountNames = accounts.map { it.name }
        val serviceDao = db.serviceDao()
        if (accountNames.isEmpty())
            serviceDao.deleteAll()
        else
            serviceDao.deleteExceptAccounts(accountNames.toTypedArray())

        // Delete orphan address book accounts (where db collection is missing)
        val addressBookAccountType = context.getString(R.string.account_type_address_book)
        deleteOrphanAddressBookAccounts(accountManager.getAccountsByType(addressBookAccountType))
    }

    /**
     * Deletes address book accounts if they do not have a corresponding collection
     * @param addressBookAccounts Address book accounts to check
     */
    @VisibleForTesting
    internal fun deleteOrphanAddressBookAccounts(addressBookAccounts: Array<Account>) {
        addressBookAccounts.forEach { addressBookAccount ->
            val collection = accountManager.getUserData(addressBookAccount, USER_DATA_COLLECTION_ID)
                ?.toLongOrNull()
                ?.let { collectionId ->
                    collectionRepository.get(collectionId)
                }
            if (collection == null) {
                // If no collection for this address book exists, we can delete it
                logger.info("Deleting address book account without collection: $addressBookAccount ")
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