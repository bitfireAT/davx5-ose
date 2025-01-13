/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

// Automatically redirects to MainActivity. Should be removed in the future.
// Needed for backwards compatibility with external apps.
class AccountsActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MainActivity.legacyRedirect(this) {
            putExtra(MainActivity.EXTRA_SYNC_ACCOUNTS, action == Intent.ACTION_SYNC)
        }
    }
}
