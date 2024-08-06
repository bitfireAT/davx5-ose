/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import at.bitfire.davdroid.ui.MainActivity

@Deprecated("Remove this activity and use Compose Navigation")
class AccountActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val account = intent.getParcelableExtra(EXTRA_ACCOUNT) as? Account
            ?: throw IllegalArgumentException("AccountActivity requires EXTRA_ACCOUNT")

        startActivity(Intent(this, MainActivity::class.java).apply {
            data = accountDeepLink(account.name)
        })
        finish()
    }

}