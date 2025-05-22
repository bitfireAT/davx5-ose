/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import AccountScreen
import android.accounts.Account
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import at.bitfire.davdroid.R
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

        val account = intent.getParcelableExtra(EXTRA_ACCOUNT) as? Account

        // If account is null, log and redirect to accounts overview
        if (account == null) {
            logger.warning("Account not found in intent extras. Redirecting to accounts overview.")
            Toast.makeText(this, R.string.account_account_missing, Toast.LENGTH_LONG).show()
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
                    val intent = Intent(this, AccountSettingsActivity::class.java)
                    intent.putExtra(AccountSettingsActivity.EXTRA_ACCOUNT, account)
                    startActivity(intent, null)
                },
                onCreateAddressBook = {
                    val intent = Intent(this, CreateAddressBookActivity::class.java)
                    intent.putExtra(CreateAddressBookActivity.EXTRA_ACCOUNT, account)
                    startActivity(intent)
                },
                onCreateCalendar = {
                    val intent = Intent(this, CreateCalendarActivity::class.java)
                    intent.putExtra(CreateCalendarActivity.EXTRA_ACCOUNT, account)
                    startActivity(intent)
                },
                onCollectionDetails = { collection ->
                    val intent = Intent(this, CollectionActivity::class.java)
                    intent.putExtra(CollectionActivity.EXTRA_ACCOUNT, account)
                    intent.putExtra(CollectionActivity.EXTRA_COLLECTION_ID, collection.id)
                    startActivity(intent, null)
                },
                onNavUp = ::onSupportNavigateUp,
                onFinish = ::finish
            )
        }
    }

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

}