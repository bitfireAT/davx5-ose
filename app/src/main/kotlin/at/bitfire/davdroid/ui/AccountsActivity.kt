/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

@Deprecated("Automatically redirects to MainActivity. Should be removed in the future.")
class AccountsActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = intent.action
            }
        )
    }
}
