/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.TaskStackBuilder
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.AccountIdIntentSerializer
import at.bitfire.davdroid.ui.account.AccountActivity.Companion.editAccountActivityIntent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AccountSettingsActivity: AppCompatActivity() {

    companion object {
        private const val EXTRA_ACCOUNT = "account"
        private const val EXTRA_EDIT_PASSWORD = "edit_password"

        /**
         * @param editPassword  whether to open the password dialog directly
         */
        fun createIntent(context: Context, accountId: AccountId, editPassword: Boolean = false): Intent {
            return Intent(context, AccountSettingsActivity::class.java).apply {
                AccountIdIntentSerializer.addExtra(this, EXTRA_ACCOUNT, accountId)
                putExtra(EXTRA_EDIT_PASSWORD, editPassword)
            }
        }
        
        fun Intent.editAccountSettingsActivityIntent(accountId: AccountId) {
            AccountIdIntentSerializer.addExtra(this, EXTRA_ACCOUNT, accountId)
        }
    }

    private val accountId by lazy {
        AccountIdIntentSerializer.fromIntent(intent, EXTRA_ACCOUNT)
            ?: throw IllegalArgumentException("EXTRA_ACCOUNT must be set")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AccountSettingsScreen(
                accountId = accountId,
                editPassword = intent.getBooleanExtra(EXTRA_EDIT_PASSWORD, false),
                onNavWifiPermissionsScreen = {
                    val intent = WifiPermissionsActivity.createIntent(this, accountId)
                    startActivity(intent)
                },
                onNavUp = ::onSupportNavigateUp,
            )
        }
    }

    override fun supportShouldUpRecreateTask(targetIntent: Intent) = true

    override fun onPrepareSupportNavigateUpTaskStack(builder: TaskStackBuilder) {
        builder.editIntentAt(builder.intentCount - 1)?.editAccountActivityIntent(accountId)
    }

}