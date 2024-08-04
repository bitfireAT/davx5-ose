/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import at.bitfire.davdroid.ui.account.accountDestination
import at.bitfire.davdroid.ui.account.navigateToAccount
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity: AppCompatActivity() {

    companion object {
        const val START_DESTINATION = "startDestination"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // handle "Sync all" intent from launcher shortcut
        val syncAccounts = intent.action == Intent.ACTION_SYNC

        val startDestination = intent.getSerializableExtra(START_DESTINATION) ?: AccountsDestination

        setContent {
            // set up Navigation
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = startDestination
            ) {
                accountsDestination(
                    initialSyncAccounts = syncAccounts,
                    onClose = ::finish,
                    onNavigateToAccount = navController::navigateToAccount
                )
                accountDestination()
            }
        }
    }

}