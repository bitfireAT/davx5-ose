/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.account

import android.accounts.Account
import android.content.Context
import android.os.Bundle
import at.bitfire.synctools.util.AndroidAccountUtils
import at.bitfire.synctools.util.SensitiveString

object SystemAccountUtils {

    /**
     * Creates a system account and makes sure the user data are set correctly.
     *
     * @param context  operating context
     * @param account  account to create
     * @param userData user data to set (only [String] values are taken into account!)
     * @param password password to set
     *
     * @return whether the account has been created
     *
     * @throws IllegalArgumentException when user data contains non-String values
     * @throws IllegalStateException if user data can't be set
     */
    @Deprecated(
        "Replace by AndroidAccountUtils",
        replaceWith = ReplaceWith("AndroidAccountUtils.createAccount()", "at.bitfire.synctools.util.AndroidAccountUtils")
    )
    fun createAccount(context: Context, account: Account, userData: Bundle, password: SensitiveString? = null): Boolean {
        val data = buildMap {
            for (key in userData.keySet()) {
                val value = userData.getString(key)
                if (value != null)
                    put(key, value)
            }
        }
        return AndroidAccountUtils.createAccount(context, account, data, password)
    }

}