/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavDeepLinkRequest
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import at.bitfire.davdroid.ui.AccountsDrawerHandler
import at.bitfire.davdroid.ui.AccountsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.logging.Logger
import javax.inject.Inject

@Composable
fun Navigation(deepLinkUri: Uri?) {
    val model = hiltViewModel<NavigationModel>()
    Navigation_WithModel(
        deepLinkUri = deepLinkUri,
        accountsDrawerHandler = model.accountsDrawerHandler,
        logger = model.logger,
    )
}

@Composable
private fun Navigation_WithModel(
    deepLinkUri: Uri?,
    accountsDrawerHandler: AccountsDrawerHandler,
    logger: Logger
) {
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        if (deepLinkUri != null) {
            val deepLinkRq = NavDeepLinkRequest.Builder.fromUri(deepLinkUri).build()
            logger.info("Got deep link: $deepLinkUri → $deepLinkRq")
            navController.navigate(deepLinkRq)
        }
    }

    NavHost(
        navController = navController,
        startDestination = Destination.Accounts()
    ) {
        composable<Destination.Accounts>(
            deepLinks = Destination.Accounts.deepLinks
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<Destination.Accounts>()
            AccountsScreen(
                initialSyncAccounts = route.syncAccounts,
                accountsDrawerHandler = accountsDrawerHandler
            )
        }
    }
}

// only for dependency injection
@HiltViewModel
class NavigationModel @Inject constructor(
    val accountsDrawerHandler: AccountsDrawerHandler,
    val logger: Logger
): ViewModel()