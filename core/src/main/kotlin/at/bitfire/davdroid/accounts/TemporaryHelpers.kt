/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.accounts

import android.accounts.Account as AndroidAccount

@Deprecated("Only used during conversion from android.accounts.Account to AccountId")
fun AndroidAccount.toAccountId(): AccountId {
    return LegacyAccount(this)
}
