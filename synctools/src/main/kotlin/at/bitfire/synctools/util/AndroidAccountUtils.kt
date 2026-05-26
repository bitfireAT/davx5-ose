/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.util

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.os.Bundle

object AndroidAccountUtils {

    /**
     * Creates a system account and makes sure the user data are set correctly.
     *
     * @param context  operating context
     * @param account  account to create
     * @param userData user data to set
     * @param password password to set
     *
     * @return whether the account has been created
     */
    fun createAccount(context: Context, account: Account, userData: Map<String, String>, password: SensitiveString? = null): Boolean {
        val userDataBundle = Bundle(userData.size).apply {
            for ((key, value) in userData)
                putString(key, value)
        }

        // create account
        val manager = AccountManager.get(context)
        if (!manager.addAccountExplicitly(account, password?.asString(), userDataBundle))
            return false

        // Android seems to lose the initial user data sometimes, so make sure that the values are set
        for ((key, value) in userData)
            manager.setAndVerifyUserData(account, key, value)

        return true
    }

}