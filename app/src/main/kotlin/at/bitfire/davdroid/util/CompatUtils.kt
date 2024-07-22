/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import android.accounts.Account
import android.accounts.AccountManager
import java.util.logging.Logger

/**
 * [AccountManager.setUserData] has been found to be unreliable at times. This extension function
 * checks whether the user data has actually been set and retries up to ten times before failing silently.
 *
 * Note: In the future we want to store accounts + associated data in the database, never calling
 * so this method will become obsolete then.
 */
@Deprecated("Don't use AccountManager to store user data; use DB instead")
fun AccountManager.setAndVerifyUserData(account: Account, key: String, value: String?) {
    for (i in 1..10) {
        setUserData(account, key, value)
        if (getUserData(account, key) == value)
            return /* success */

        Thread.sleep(100)
    }
    Logger.getGlobal().warning("AccountManager failed to set $account user data $key := $value")
}