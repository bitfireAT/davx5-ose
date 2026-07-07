/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.TaskStackBuilder
import androidx.core.content.IntentCompat
import at.bitfire.davdroid.ui.account.AccountActivity.Companion.editAccountActivityIntent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AccountSettingsActivity: AppCompatActivity() {

    companion object {
        private const val EXTRA_ACCOUNT = "account"
        
        fun createIntent(context: Context, account: Account): Intent {
            return Intent(context, AccountSettingsActivity::class.java).apply { 
                putExtra(EXTRA_ACCOUNT, account)
            }
        }
        
        fun Intent.editAccountSettingsActivityIntent(account: Account) {
            putExtra(EXTRA_ACCOUNT, account)
        }
    }

    private val account by lazy {
        IntentCompat.getParcelableExtra(intent, EXTRA_ACCOUNT, Account::class.java) ?: throw IllegalArgumentException("EXTRA_ACCOUNT must be set")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = account.name

        setContent {
            AccountSettingsScreen(
                account = account,
                onNavWifiPermissionsScreen = {
                    val intent = WifiPermissionsActivity.createIntent(this, account)
                    startActivity(intent)
                },
                onNavUp = ::onSupportNavigateUp,
            )
        }
    }

    override fun supportShouldUpRecreateTask(targetIntent: Intent) = true

    override fun onPrepareSupportNavigateUpTaskStack(builder: TaskStackBuilder) {
        builder.editIntentAt(builder.intentCount - 1)?.editAccountActivityIntent(account)
    }

}