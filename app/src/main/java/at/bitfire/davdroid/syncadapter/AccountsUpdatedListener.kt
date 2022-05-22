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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.util.logging.Level

class AccountsUpdatedListener(val context: Context): KoinComponent, OnAccountsUpdateListener {

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
     */
    @AnyThread
    override fun onAccountsUpdated(accounts: Array<out Account>) {
        /* onAccountsUpdated may be called from the main thread, but cleanupAccounts
           requires disk (database) access. So we launch it in a separate thread. */
        CoroutineScope(Dispatchers.Default).launch {
            cleanupAccounts(context, accounts)
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
        val serviceDao = get<AppDatabase>().serviceDao()
        if (mainAccountNames.isEmpty())
            serviceDao.deleteAll()
        else
            serviceDao.deleteExceptAccounts(mainAccountNames.toTypedArray())
    }

}