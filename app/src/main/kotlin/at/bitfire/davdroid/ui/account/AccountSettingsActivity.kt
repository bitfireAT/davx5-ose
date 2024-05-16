/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import android.accounts.Account
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.TaskStackBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AccountSettingsActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    private val account by lazy {
        intent.getParcelableExtra<Account>(EXTRA_ACCOUNT) ?: throw IllegalArgumentException("EXTRA_ACCOUNT must be set")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = account.name

        setContent {
            AccountSettingsScreen(
                onNavUp = { onSupportNavigateUp() },
                onSyncWifiOnlyPermissionsAction = {
                    val intent = Intent(this, WifiPermissionsActivity::class.java)
                    intent.putExtra(WifiPermissionsActivity.EXTRA_ACCOUNT, account)
                    startActivity(intent)
                },
                account = account
            )
        }
    }

    override fun supportShouldUpRecreateTask(targetIntent: Intent) = true

    override fun onPrepareSupportNavigateUpTaskStack(builder: TaskStackBuilder) {
        builder.editIntentAt(builder.intentCount - 1)?.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
    }

}