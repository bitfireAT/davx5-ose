/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import at.bitfire.davdroid.ui.navigation.Destination

/**
 * Legacy activity, previously used to host [AccountsScreen].
 * Needed for backwards compatibility with external apps / shortcuts that call the activity directly.
 */
class AccountsActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = Destination.Accounts.PATH.toUri().buildUpon()
        val syncOnLaunch = intent.action == Intent.ACTION_SYNC
        if (syncOnLaunch)
            uri.appendQueryParameter("syncAccounts", "true")

        MainActivity.legacyRedirect(
            activity = this,
            uri = uri.build()
        )
    }

}