/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
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

            CompositionLocalProvider(LocalNavController provides navController) {
                NavHost(
                    navController = navController,
                    startDestination = accountsFromIntent()
                ) {
                    composable<Destination.Accounts> {
                        AccountsScreen(it, accountsDrawerHandler)
                    }
                }
            }
        }
    }

    /**
     * Initializes the accounts route from the current intent data.
     * Checks whether the action is [Intent.ACTION_SYNC].
     */
    private fun accountsFromIntent() = Destination.Accounts(
        // handle "Sync all" intent from launcher shortcut
        syncAccounts = intent.getBooleanExtra(EXTRA_SYNC_ACCOUNTS, false)
    )


    companion object {
        /**
         * Used by the "Sync all" intent from launcher shortcut.
         */
        const val EXTRA_SYNC_ACCOUNTS = "sync-accounts"

        /**
         * Starts [MainActivity] as a redirection of a legacy activity.
         * By default, copies all the intent data from [activity].
         * This is used for migrating all multi-activity logic, to single-activity.
         *
         * @param activity The activity that is requesting the redirection.
         * @param builder Any modifications or extras that you want to add to the intent should be
         * placed here.
         */
        @UiThread
        fun legacyRedirect(activity: Activity, builder: Intent.() -> Unit = {}) {
            val intent = activity.intent

            activity.startActivity(
                Intent(activity, MainActivity::class.java).apply {
                    // Create a new activity, do not allow going back.
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    // Copy the action called.
                    action = intent.action
                    // Copy any intent data.
                    data = intent.data
                    // Copy all extras.
                    putExtras(intent)

                    // Perform any extra modifications required
                    builder()
                }
            )
        }
    }

}