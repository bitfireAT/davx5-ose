/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.accounts

import android.accounts.Account

/**
 * Android account of a legacy account or of a DB account.
 *
 * @param isLegacy           whether [account] is the Android account of a legacy account
 * @param linkedDbAccountId  ID of the DB account the Android account is linked to; `null` for legacy and unlinked accounts
 */
data class AndroidAccountInfo(
    val account: Account,
    val isLegacy: Boolean,
    val linkedDbAccountId: Long? = null
)
