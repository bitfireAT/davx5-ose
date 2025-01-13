package at.bitfire.davdroid.ui.navigation

import kotlinx.serialization.Serializable

object Destination {

    @Serializable
    data class Accounts(val syncAccounts: Boolean)

}