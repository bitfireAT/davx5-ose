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
import androidx.core.content.IntentCompat
import at.bitfire.davdroid.R
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

        val account =
            IntentCompat.getParcelableExtra(intent, EXTRA_ACCOUNT, Account::class.java) ?:
            intent.getStringExtra(EXTRA_ACCOUNT)?.let { Account(it, getString(R.string.account_type)) }

        // If account is not passed, log warning and redirect to accounts overview
        if (account == null) {
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
                account = account,
                onAccountSettings = {
                    val intent = AccountSettingsActivity.createIntent(this, account.toAccountId())
                    startActivity(intent, null)
                },
                onCreateAddressBook = {
                    val intent = CreateAddressBookActivity.createIntent(this, account)
                    startActivity(intent)
                },
                onCreateCalendar = {
                    val intent = CreateCalendarActivity.createIntent(this, account.toAccountId())
                    startActivity(intent)
                },
                onCollectionDetails = { collection ->
                    val intent = CollectionActivity.createIntent(this, account.toAccountId(), collection.id)
                    startActivity(intent, null)
                },
                onNavUp = ::onSupportNavigateUp,
                onFinish = ::finish
            )
        }
    }

    companion object {
        private const val EXTRA_ACCOUNT = "account"
        
        fun createIntent(context: Context, account: Account): Intent {
            return Intent(context, AccountActivity::class.java).apply { 
                putExtra(EXTRA_ACCOUNT, account)
            }
        }
        
        fun Intent.editAccountActivityIntent(account: Account) {
            putExtra(EXTRA_ACCOUNT, account)
        }
    }

}