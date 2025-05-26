/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import at.bitfire.davdroid.ui.AccountsScreen

@Composable
fun Navigation(
    initialDestination: Destination = Destination.Accounts()
) {
    val backStack = remember { mutableStateListOf<Any>(initialDestination) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = { key ->
            when (key) {
                is Destination.Accounts -> NavEntry(key) {
                    AccountsScreen(
                        initialSyncAccounts = key.syncAccounts,
                    )
                }

                else -> NavEntry(Unit) { Text("Unknown route") }
            }
        }
    )
}
