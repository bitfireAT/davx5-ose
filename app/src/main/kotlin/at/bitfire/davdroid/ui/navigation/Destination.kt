package at.bitfire.davdroid.ui.navigation

import androidx.navigation.navDeepLink
import kotlinx.serialization.Serializable

object Destination {

    private const val BASE_URI = "davx5://destination"

    @Serializable
    data class Accounts(val syncAccounts: Boolean = false) {
        companion object {
            const val PATH = "$BASE_URI/accounts"

            val deepLinks = listOf(
                navDeepLink<Accounts>(basePath = PATH)
            )
        }
    }

}
