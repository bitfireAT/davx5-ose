/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import kotlinx.serialization.Serializable

@Serializable
data class AccountDestination(
    val accountName: String
)

fun accountDeepLink(accountName: String) = "${BuildConfig.APPLICATION_ID}://account/$accountName".toUri()

fun NavController.navigateToAccount(accountName: String) {
    navigate(AccountDestination(accountName))
}

fun NavGraphBuilder.accountDestination() {
    composable<AccountDestination>(
        deepLinks = listOf(
            navDeepLink { uriPattern = "${BuildConfig.APPLICATION_ID}://account/{accountName}" }
        )
    ) { backStackEntry ->
        val destination: AccountDestination = backStackEntry.toRoute()
        val account = Account(destination.accountName, stringResource(R.string.account_type))

        val context = LocalContext.current
        val navController = rememberNavController()
        AccountScreen(
            account = account,
            onAccountSettings = {
                val intent = Intent(context, AccountSettingsActivity::class.java)
                intent.putExtra(AccountSettingsActivity.EXTRA_ACCOUNT, account)
                context.startActivity(intent, null)
            },
            onCreateAddressBook = {
                val intent = Intent(context, CreateAddressBookActivity::class.java)
                intent.putExtra(CreateAddressBookActivity.EXTRA_ACCOUNT, account)
                context.startActivity(intent)
            },
            onCreateCalendar = {
                val intent = Intent(context, CreateCalendarActivity::class.java)
                intent.putExtra(CreateCalendarActivity.EXTRA_ACCOUNT, account)
                context.startActivity(intent)
            },
            onCollectionDetails = { collection ->
                val intent = Intent(context, CollectionActivity::class.java)
                intent.putExtra(CollectionActivity.EXTRA_ACCOUNT, account)
                intent.putExtra(CollectionActivity.EXTRA_COLLECTION_ID, collection.id)
                context.startActivity(intent, null)
            },
            onNavUp = {
                navController.navigateUp()
            }
        )
    }
}