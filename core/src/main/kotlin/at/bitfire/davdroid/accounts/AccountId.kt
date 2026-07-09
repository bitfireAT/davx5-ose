/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.accounts

import android.accounts.Account as AndroidAccount

/**
 * Uniquely identifies an app account.
 */
sealed interface AccountId

/**
 * A legacy account that is backed by Android's AccountManager.
 */
class LegacyAccount(val androidAccount: AndroidAccount) : AccountId {
    override fun toString(): String {
        return "${androidAccount.type}/${androidAccount.name}"
    }
}
