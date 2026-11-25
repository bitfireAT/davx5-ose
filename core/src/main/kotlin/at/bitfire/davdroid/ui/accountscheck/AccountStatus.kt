/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.accountscheck

import android.accounts.Account

/**
 * Status of a DAVx⁵ account, as shown in the "Android accounts" view.
 */
sealed interface AccountStatus {

    val name: String

    /** whether the account has a problem that is shown with a warning */
    val isProblem: Boolean
        get() = when (this) {
            is Legacy, is Linked -> false
            is MissingDbAccount -> true
        }

    /** Android account of a legacy account (services reference it by name) */
    data class Legacy(
        override val name: String
    ): AccountStatus

    /** Android account that links to an existing DB account */
    data class Linked(
        override val name: String
    ): AccountStatus

    /** Android account that doesn't link to an existing DB account */
    data class MissingDbAccount(
        val account: Account
    ): AccountStatus {
        override val name: String
            get() = account.name
    }

}
