/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.accounts

import android.content.Intent
import androidx.core.content.IntentCompat
import android.accounts.Account as AndroidAccount

object AccountIdIntentSerializer {
    fun addExtra(intent: Intent, key: String, accountId: AccountId) {
        when (accountId) {
            is LegacyAccount -> intent.putExtra(key, accountId.androidAccount)
            is DbAccountId -> intent.putExtra(key, accountId.id)
        }
    }

    fun fromIntent(intent: Intent, key: String): AccountId? {
        // Try getting the account as a Parcelable first
        IntentCompat.getParcelableExtra(intent, key, AndroidAccount::class.java)?.let { androidAccount ->
            return LegacyAccount(androidAccount)
        }
        // If that fails, try getting it as a Long
        intent.getLongExtra(key, 0L).takeIf { it > 0L }?.let { id ->
            return DbAccountId(id)
        }
        // If none works, return null
        return null
    }
}
