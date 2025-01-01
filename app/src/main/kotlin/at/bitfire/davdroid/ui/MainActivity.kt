/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import at.bitfire.davdroid.ui.composition.LocalNavController
import at.bitfire.davdroid.ui.navigation.Routes
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity: AppCompatActivity() {

    @Inject
    lateinit var accountsDrawerHandler: AccountsDrawerHandler


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val navController = rememberNavController()

            CompositionLocalProvider(LocalNavController provides navController) {
                NavHost(
                    navController = navController,
                    startDestination = accountsFromIntent()
                ) {
                    composable<Routes.Accounts> { AccountsScreen(it, accountsDrawerHandler) }
                }
            }
        }
    }

    /**
     * Initializes the accounts route from the current intent data.
     * Checks whether the action is [Intent.ACTION_SYNC].
     */
    private fun accountsFromIntent() = Routes.Accounts(
        // handle "Sync all" intent from launcher shortcut
        syncAccounts = intent.action == Intent.ACTION_SYNC
    )

}