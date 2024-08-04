/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import at.bitfire.davdroid.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
@Deprecated("Use Compose Navigation instead")
class AccountActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val account = intent.getParcelableExtra(EXTRA_ACCOUNT) as? Account
            ?: throw IllegalArgumentException("AccountActivity requires EXTRA_ACCOUNT")

        // TODO remove this activity and use Compose Navigation
        startActivity(Intent(this, MainActivity::class.java).apply {
            data = accountDeepLink(account.name)
            addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP)
        })
    }

}