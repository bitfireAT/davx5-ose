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
            is DbAccountId -> intent.putExtras(accountId.toBundle(key))
        }
    }

    fun fromIntent(intent: Intent, key: String): AccountId? {
        return IntentCompat.getParcelableExtra(intent, key, AndroidAccount::class.java)?.let { androidAccount ->
            LegacyAccount(androidAccount)
        } ?: intent.extras?.let { DbAccountId.fromBundle(it, key) }
    }
}
