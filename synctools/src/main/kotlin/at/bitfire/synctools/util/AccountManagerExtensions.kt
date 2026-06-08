/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.util

import android.accounts.Account
import android.accounts.AccountManager
import java.util.logging.Logger

/**
 * [AccountManager.setUserData] has been found to be unreliable at times. This extension function
 * checks whether the user data has actually been set and retries up to ten times before it gives up,
 * without throwing an Exception.
 *
 * Account user data should only be used to reference an own reliable storage.
 */
fun AccountManager.setAndVerifyUserData(account: Account, key: String, value: String?) {
    for (i in 1..10) {
        if (getUserData(account, key) == value)
            return  /* already set / success */

        setUserData(account, key, value)

        // wait a bit because AccountManager access sometimes seems a bit asynchronous
        Thread.sleep(100)
    }

    Logger.getGlobal().warning("AccountManager failed to set $account user data $key := $value")
}
