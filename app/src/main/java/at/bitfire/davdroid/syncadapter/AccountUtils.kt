/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.content.Context
import android.os.Bundle
import androidx.annotation.AnyThread
import at.bitfire.davdroid.R
import at.bitfire.davdroid.Singleton
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.AppDatabase
import at.bitfire.davdroid.resource.LocalAddressBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.logging.Level

object AccountUtils {

    /**
     * Creates an account and makes sure the user data are set correctly.
     *
     * @param context  operating context
     * @param account  account to create
     * @param userData user data to set
     *
     * @return whether the account has been created
     *
     * @throws IllegalArgumentException when user data contains non-String values
     * @throws IllegalStateException if user data can't be set
     */
    fun createAccount(context: Context, account: Account, userData: Bundle, password: String? = null): Boolean {
        // validate user data
        for (key in userData.keySet()) {
            userData.get(key)?.let { entry ->
                if (entry !is String)
                    throw IllegalArgumentException("userData[$key] is ${entry::class.java} (expected: String)")
            }
        }

        // create account
        val manager = AccountManager.get(context)
        if (!manager.addAccountExplicitly(account, password, userData))
            return false

        // Android seems to lose the initial user data sometimes, so set it a second time if that happens
        // https://forums.bitfire.at/post/11644
        if (!verifyUserData(context, account, userData))
            for (key in userData.keySet())
                manager.setUserData(account, key, userData.getString(key))

        if (!verifyUserData(context, account, userData))
            throw IllegalStateException("Android doesn't store user data in account")

        return true
    }

    fun registerAccountsUpdateListener(context: Context) {
        val listener = Singleton.getInstance(context) {
            AccountsUpdatedListener(it)
        }

        val accountManager = AccountManager.get(context)
        accountManager.addOnAccountsUpdatedListener(listener, null, true)
    }

    private fun verifyUserData(context: Context, account: Account, userData: Bundle): Boolean {
        val accountManager = AccountManager.get(context)
        userData.keySet().forEach { key ->
            val stored = accountManager.getUserData(account, key)
            val expected = userData.getString(key)
            if (stored != expected) {
                Logger.log.warning("Stored user data \"$stored\" differs from expected data \"$expected\" for $key")
                return false
            }
        }
        return true
    }


    class AccountsUpdatedListener(val context: Context): OnAccountsUpdateListener {

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
            val db = AppDatabase.getInstance(context)
            val serviceDao = db.serviceDao()
            if (mainAccountNames.isEmpty())
                serviceDao.deleteAll()
            else
                serviceDao.deleteExceptAccounts(mainAccountNames.toTypedArray())
        }

    }

}