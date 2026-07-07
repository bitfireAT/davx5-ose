/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.accounts

import android.accounts.Account as AndroidAccount

@Deprecated("Only used during conversion from android.accounts.Account to AccountId")
fun AccountId.toAndroidAccount(): AndroidAccount {
    require(this is LegacyAccount) { 
        "Account instance must be a LegacyAccount, but was ${this.javaClass.canonicalName}" 
    }
    
    return androidAccount
}

@Deprecated("Only used during conversion from android.accounts.Account to AccountId")
fun AndroidAccount.toAccountId(): AccountId {
    return LegacyAccount(this)
}
