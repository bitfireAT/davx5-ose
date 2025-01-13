/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.core.net.toUri
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import at.bitfire.davdroid.ui.navigation.Destination
import at.bitfire.davdroid.ui.navigation.LocalNavController
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

            LaunchedEffect(Unit) { intent.data?.let(navController::navigate) }

            CompositionLocalProvider(LocalNavController provides navController) {
                NavHost(
                    navController = navController,
                    startDestination = Destination.Accounts()
                ) {
                    composable<Destination.Accounts>(
                        deepLinks = Destination.Accounts.deepLinks
                    ) {
                        AccountsScreen(it, accountsDrawerHandler)
                    }
                }
            }
        }
    }


    companion object {

        fun legacyIntent(context: Context, uri: String): Intent {
            return Intent(
                Intent.ACTION_VIEW,
                uri.toUri(),
                context,
                MainActivity::class.java
            ).apply {
                // Create a new activity, do not allow going back.
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        /**
         * Starts [MainActivity] as a redirection of a legacy activity.
         * This is used for migrating all multi-activity logic, to single-activity.
         *
         * @param activity The activity that is requesting the redirection.
         * @param uri The URI to launch. Should have schema `davx5://`
         */
        @UiThread
        fun legacyRedirect(activity: Activity, uri: String) {
            activity.startActivity(
                legacyIntent(activity, uri)
            )
        }

    }

}
