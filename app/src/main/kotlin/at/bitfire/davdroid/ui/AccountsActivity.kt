/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import at.bitfire.davdroid.ui.navigation.Destination

/**
 * Legacy activity, previously used to host [AccountsScreen].
 * Needed for backwards compatibility with external apps / shortcuts that call the activity directly.
 */
class AccountsActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MainActivity.legacyRedirect(
            activity = this,
            uri = Destination.Accounts.PATH + "?syncAccounts=${intent.action == Intent.ACTION_SYNC}"
        )
    }

}