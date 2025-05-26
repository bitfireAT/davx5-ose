/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSavedStateNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.rememberSceneSetupNavEntryDecorator
import at.bitfire.davdroid.ui.AccountsScreen
import at.bitfire.davdroid.ui.NavModel

@Composable
fun Navigation(
    initialDestination: Destination = Destination.Accounts(),
    model: NavModel = viewModel(factory = NavModel.Factory(initialDestination)),
) {
    val backStack by model.backStack.collectAsState()

    NavDisplay(
        backStack = backStack,
        onBack = model::popBackStack,
        entryDecorators = listOf(
            // Add the default decorators for managing scenes and saving state
            rememberSceneSetupNavEntryDecorator(),
            rememberSavedStateNavEntryDecorator(),
            // Then add the view model store decorator
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<Destination.Accounts> { key ->
                AccountsScreen(
                    initialSyncAccounts = key.syncAccounts,
                )
            }
        },
    )
}
