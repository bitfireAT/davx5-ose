/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalAddressBook
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.util.concurrent.Semaphore
import java.util.logging.Level

@HiltWorker
class AccountsCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    val db: AppDatabase
): Worker(appContext, workerParameters) {

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
         * Enqueues [AccountsCleanupWorker] to be run regularly (but not necessarily now).
         */
        fun enqueue(context: Context) {
            // run every day
            val rq = PeriodicWorkRequestBuilder<AccountsCleanupWorker>(Duration.ofDays(1))
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, rq.build())
        }

    }

    override fun doWork(): Result {
        lockAccountsCleanup()
        try {
            val accountManager = AccountManager.get(applicationContext)
            cleanupAccounts(applicationContext, accountManager.accounts)
        } finally {
            unlockAccountsCleanup()
        }
        return Result.success()
    }

    private fun cleanupAccounts(context: Context, accounts: Array<out Account>) {
        Logger.log.log(Level.INFO, "Cleaning up accounts. Current accounts:", accounts)

        val mainAccountType = context.getString(R.string.account_type)
        val mainAccountNames = accounts
            .filter { account -> account.type == mainAccountType }
            .map { it.name }

        val addressBookAccountType = context.getString(R.string.account_type_address_book)
        val addressBooks = accounts
            .filter { account -> account.type == addressBookAccountType }
            .map { addressBookAccount -> LocalAddressBook(context, addressBookAccount, null) }
        for (addressBook in addressBooks) {
            try {
                val mainAccount = addressBook.mainAccount
                if (mainAccount == null || !mainAccountNames.contains(mainAccount.name))
                    // the main account for this address book doesn't exist anymore
                    addressBook.delete()
            } catch(e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't delete address book account", e)
            }
        }

        // delete orphaned services in DB
        val serviceDao = db.serviceDao()
        if (mainAccountNames.isEmpty())
            serviceDao.deleteAll()
        else
            serviceDao.deleteExceptAccounts(mainAccountNames.toTypedArray())
    }

}