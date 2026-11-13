/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.accountscheck

/**
 * Status of a DAVx⁵ account, as shown in "Check and repair accounts".
 */
sealed interface AccountStatus {

    val name: String

    /** Android account of a legacy account (services reference it by name) */
    data class Legacy(
        override val name: String
    ): AccountStatus

    /** Android account that links to an existing DB account */
    data class Linked(
        override val name: String
    ): AccountStatus

}
