/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.os.Bundle
import at.bitfire.davdroid.log.Logger

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


}