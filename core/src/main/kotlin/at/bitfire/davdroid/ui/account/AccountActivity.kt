/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import AccountScreen
import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import at.bitfire.davdroid.R
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.AccountIdIntentSerializer
import at.bitfire.davdroid.accounts.toAccountId
import at.bitfire.davdroid.ui.AccountsActivity
import dagger.hilt.android.AndroidEntryPoint
import java.util.logging.Logger
import javax.inject.Inject

@AndroidEntryPoint
class AccountActivity : AppCompatActivity() {

    @Inject
    lateinit var logger: Logger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val accountId = AccountIdIntentSerializer.fromIntent(intent, EXTRA_ACCOUNT)
            ?: intent.getStringExtra(EXTRA_ACCOUNT)?.let { Account(it, getString(R.string.account_type)).toAccountId() }

        // If account is not passed, log warning and redirect to accounts overview
        if (accountId == null) {
            logger.warning("AccountActivity requires EXTRA_ACCOUNT")

            // Redirect to accounts overview activity
            val intent = Intent(this, AccountsActivity::class.java).apply {
                // Create a new root activity, do not allow going back.
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            finish()
            return
        }

        setContent {
            AccountScreen(
                accountId = accountId,
                onAccountSettings = {
                    val intent = AccountSettingsActivity.createIntent(this, accountId)
                    startActivity(intent, null)
                },
                onCreateAddressBook = {
                    val intent = CreateAddressBookActivity.createIntent(this, accountId)
                    startActivity(intent)
                },
                onCreateCalendar = {
                    val intent = CreateCalendarActivity.createIntent(this, accountId)
                    startActivity(intent)
                },
                onCollectionDetails = { collection ->
                    val intent = CollectionActivity.createIntent(this, accountId, collection.id)
                    startActivity(intent, null)
                },
                onNavUp = ::onSupportNavigateUp,
                onFinish = ::finish
            )
        }
    }

    companion object {
        private const val EXTRA_ACCOUNT = "account"
        
        fun createIntent(context: Context, accountId: AccountId): Intent {
            return Intent(context, AccountActivity::class.java).apply {
                AccountIdIntentSerializer.addExtra(this, EXTRA_ACCOUNT, accountId)
            }
        }
        
        fun Intent.editAccountActivityIntent(accountId: AccountId) {
            AccountIdIntentSerializer.addExtra(this, EXTRA_ACCOUNT, accountId)
        }
    }

}