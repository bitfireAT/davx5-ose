/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.content.Context
import androidx.annotation.AnyThread
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalAddressBook
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Semaphore
import java.util.logging.Level
import javax.inject.Singleton

class AccountsUpdatedListener private constructor(
    val context: Context
): OnAccountsUpdateListener {

    @Module
    @InstallIn(SingletonComponent::class)
    object AccountsUpdatedListenerModule {
        @Provides
        @Singleton
        fun accountsUpdatedListener(@ApplicationContext context: Context) = AccountsUpdatedListener(context)
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AccountsUpdatedListenerEntryPoint {
        fun appDatabase(): AppDatabase
    }

    /**
     * This mutex (semaphore with 1 permit) will be acquired by [onAccountsUpdated]. So if you
     * want to postpone [onAccountsUpdated] execution because you're modifying accounts non-transactionally,
     * you can acquire the mutex by yourself. Don't forget to release it as soon as you're done.
     */
    val mutex = Semaphore(1)


    fun listen() {
        val accountManager = AccountManager.get(context)
        accountManager.addOnAccountsUpdatedListener(this, null, true)
    }

    /**
     * Called when the main accounts have been updated, including when a main account has been
     * removed. In the latter case, this method fulfills two tasks:
     *
     * 1. Remove related address book accounts.
     * 2. Remove related service entries from the [AppDatabase].
     *
     * Before the accounts are cleaned up, the [mutex] is acquired.
     * After the accounts are cleaned up, the [mutex] is released.
     */
    @AnyThread
    override fun onAccountsUpdated(accounts: Array<out Account>) {
        /* onAccountsUpdated may be called from the main thread, but cleanupAccounts
           requires disk (database) access. So we launch it in a separate thread. */
        CoroutineScope(Dispatchers.Default).launch {
            try {
                mutex.acquire()
                cleanupAccounts(context, accounts)
            } finally {
                mutex.release()
            }
        }
    }

    @Synchronized
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
                if (!mainAccountNames.contains(addressBook.mainAccount.name))
                // the main account for this address book doesn't exist anymore
                    addressBook.delete()
            } catch(e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't delete address book account", e)
            }
        }

        // delete orphaned services in DB
        val db = EntryPointAccessors.fromApplication(context, AccountsUpdatedListenerEntryPoint::class.java).appDatabase()
        val serviceDao = db.serviceDao()
        if (mainAccountNames.isEmpty())
            serviceDao.deleteAll()
        else
            serviceDao.deleteExceptAccounts(mainAccountNames.toTypedArray())
    }

}