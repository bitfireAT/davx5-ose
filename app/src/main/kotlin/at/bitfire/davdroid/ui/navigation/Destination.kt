/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.navigation

sealed interface Destination {
    data class Accounts(val syncAccounts: Boolean = false): Destination
}
