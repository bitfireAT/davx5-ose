/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import at.bitfire.davdroid.ui.navigation.Destination
import at.bitfire.davdroid.ui.navigation.Navigation
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity: ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // handle "Sync all" intent from launcher shortcut
        val syncAccounts = intent.action == Intent.ACTION_SYNC

        setContent {
            Navigation(
                initialDestination = Destination.Accounts(syncAccounts),
            )
        }
    }

}
