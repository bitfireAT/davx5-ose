/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.sync.account

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.os.Bundle
import at.bitfire.davdroid.util.setAndVerifyUserData

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

        // Android seems to lose the initial user data sometimes, so make sure that the values are set
        for (key in userData.keySet())
            manager.setAndVerifyUserData(account, key, userData.getString(key))

        return true
    }

}