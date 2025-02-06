/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.account

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.os.Bundle
import at.bitfire.davdroid.BuildConfig
import java.util.logging.Logger

object SystemAccountUtils {

    /**
     * Creates a system account, disables synchronization and makes sure the user data are set correctly.
     *
     * Never call [AccountManager.addAccountExplicitly] directly, use this method instead!
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
    fun createAccount(context: Context, account: Account, userData: Bundle?, password: String? = null): Boolean {
        // Validate user data
        if (userData != null)
            for (key in userData.keySet()) {
                userData.get(key)?.let { entry ->
                    if (entry !is String)
                        throw IllegalArgumentException("userData[$key] is ${entry::class.java} (expected: String)")
                }
            }

        // Create account
        val manager = AccountManager.get(context)
        if (!manager.addAccountExplicitly(account, password, userData))
            return false

        // Disable all possible synchronization. If any sync adapter is needed, it should be enabled explicitly, usually by AutomaticSyncManager.
        /*val authorities = when (account.type) {
            context.getString(R.string.account_type) ->
                arrayOf(
                    CalendarContract.AUTHORITY,
                    *TaskProvider.ProviderName.entries.map { it.authority }.toTypedArray()
                )
            context.getString(R.string.account_type_address_book) ->
                arrayOf(ContactsContract.AUTHORITY)
            else ->
                throw IllegalArgumentException("Unknown account type: ${account.type}")
        }
        for (authority in authorities)
            ContentResolver.setIsSyncable(account, authority, 0)*/

        // Android seems to lose the initial user data sometimes, so make sure that the values are set
        if (userData != null)
            for (key in userData.keySet())
                manager.setAndVerifyUserData(account, key, userData.getString(key))

        return true
    }

}

/**
 * [AccountManager.setUserData] has been found to be unreliable at times. This extension function
 * checks whether the user data has actually been set and retries up to ten times before failing silently.
 *
 * It should only be used to store the reference to the database (like the collection ID that this account represents).
 * Everything else should be in the DB.
 */
fun AccountManager.setAndVerifyUserData(account: Account, key: String, value: String?) {
    for (i in 1..10) {
        if (getUserData(account, key) == value)
            /* already set / success */
            return

        if (!accounts.contains(account))
            /* invalid account */
            return

        // set user data
        setUserData(account, key, value)

        // wait a bit because AccountManager access sometimes seems a bit asynchronous
        Thread.sleep(100)
    }

    val message = "AccountManager.setAndVerifyUserData() failed to set $account user data $key := $value"
    if (BuildConfig.DEBUG)
        throw RuntimeException(message)
    else
        Logger.getGlobal().warning("AccountManager failed to set $account user data $key := $value")
}