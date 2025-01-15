package at.bitfire.davdroid.ui.navigation

import androidx.navigation.navDeepLink
import kotlinx.serialization.Serializable

object Destination {

    /**
     * Base URI for internal (non-exposed) deep links (used for navigation).
     */
    private const val APP_BASE_URI = "nav:"

    @Serializable
    data class Accounts(val syncAccounts: Boolean = false) {
        companion object {
            const val PATH = "$APP_BASE_URI/accounts"

            val deepLinks = listOf(
                navDeepLink<Accounts>(basePath = PATH)
            )
        }
    }

}