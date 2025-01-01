package at.bitfire.davdroid.ui.navigation

import kotlinx.serialization.Serializable

object Routes {
    @Serializable
    data class Accounts(val syncAccounts: Boolean)
}
