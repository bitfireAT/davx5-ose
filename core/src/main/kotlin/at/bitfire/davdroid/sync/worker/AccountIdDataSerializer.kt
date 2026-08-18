/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.worker

import android.accounts.Account
import androidx.work.Data
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.DbAccountId
import at.bitfire.davdroid.accounts.LegacyAccount

private const val INPUT_ACCOUNT_ID = "accountId"
private const val INPUT_ACCOUNT_NAME = "accountName"
private const val INPUT_ACCOUNT_TYPE = "accountType"

fun Data.Builder.putAccountId(accountId: AccountId): Data.Builder {
    when (accountId) {
        is LegacyAccount -> {
            val account = accountId.androidAccount
            putString(INPUT_ACCOUNT_NAME, account.name)
            putString(INPUT_ACCOUNT_TYPE, account.type)
        }
        is DbAccountId -> {
            putLong(INPUT_ACCOUNT_ID, accountId.id)
        }
    }

    return this
}

fun Data.getAccountId(): AccountId? {
    val accountId = getLong(INPUT_ACCOUNT_ID, 0L)
    val accountName = getString(INPUT_ACCOUNT_NAME)
    val accountType = getString(INPUT_ACCOUNT_TYPE)

    if (accountName != null && accountType != null) {
        val account = Account(accountName, accountType)
        return LegacyAccount(account)
    } else if (accountId > 0) {
        return DbAccountId(accountId)
    }

    return null
}
