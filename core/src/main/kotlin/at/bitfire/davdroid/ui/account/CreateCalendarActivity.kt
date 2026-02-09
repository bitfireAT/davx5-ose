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
import at.bitfire.davdroid.ui.composable.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CreateCalendarActivity: AppCompatActivity() {

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    val account by lazy {
        intent.getParcelableExtra<Account>(EXTRA_ACCOUNT) ?: throw IllegalArgumentException("EXTRA_ACCOUNT must be set")
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                CreateCalendarScreen(
                    account = account,
                    onNavUp = ::onSupportNavigateUp,
                    onFinish = ::finish
                )
            }
        }
    }

    override fun supportShouldUpRecreateTask(targetIntent: Intent) = true

    override fun onPrepareSupportNavigateUpTaskStack(builder: TaskStackBuilder) {
        builder.editIntentAt(builder.intentCount - 1)?.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
    }

}