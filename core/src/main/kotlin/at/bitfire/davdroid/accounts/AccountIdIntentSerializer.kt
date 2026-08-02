/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.accounts

import android.content.Intent
import androidx.core.content.IntentCompat
import android.accounts.Account as AndroidAccount

object AccountIdIntentSerializer {
    fun addExtra(intent: Intent, key: String, accountId: AccountId) {
        val value = when (accountId) {
            is LegacyAccount -> accountId.androidAccount
        }
        intent.putExtra(key, value)
    }
    
    fun fromIntent(intent: Intent, key: String): AccountId? {
        return IntentCompat.getParcelableExtra(intent, key, AndroidAccount::class.java)?.let { androidAccount ->
            LegacyAccount(androidAccount)
        }
    }
}
