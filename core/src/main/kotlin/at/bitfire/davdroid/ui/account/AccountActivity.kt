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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import at.bitfire.davdroid.R
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.accounts.AccountIdIntentSerializer
import at.bitfire.davdroid.accounts.AndroidAccountManager
import at.bitfire.davdroid.ui.AccountsActivity
import dagger.hilt.android.AndroidEntryPoint
import java.util.logging.Logger
import javax.inject.Inject

@AndroidEntryPoint
class AccountActivity : AppCompatActivity() {

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var androidAccountManager: AndroidAccountManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If account is not passed, log warning and redirect to accounts overview
        if (!intent.hasExtra(EXTRA_ACCOUNT_ID) && !intent.hasExtra(EXTRA_ACCOUNT)) {
            logger.warning("AccountActivity requires EXTRA_ACCOUNT_ID or EXTRA_ACCOUNT")

            redirectToAccountsOverview()
            return
        }

        setContent {
            var mutableAccountId by remember { mutableStateOf<AccountId?>(null) }

            LaunchedEffect(Unit) {
                val accountId = loadAccountId()
                if (accountId != null) {
                    mutableAccountId = accountId
                } else {
                    redirectToAccountsOverview()
                }
            }

            val accountId = mutableAccountId
            if (accountId != null) {
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
    }

    private fun redirectToAccountsOverview() {
        val intent = Intent(this, AccountsActivity::class.java).apply {
            // Create a new root activity, do not allow going back.
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)

        finish()
    }

    // TODO: Move this functionality to AccountScreenViewModel the next time it is refactored.
    private suspend fun loadAccountId(): AccountId? {
        return extractAccountIdFromExtra() ?: fetchAccountIdForAndroidAccount()
    }

    private fun extractAccountIdFromExtra(): AccountId? {
        return AccountIdIntentSerializer.fromIntent(intent, EXTRA_ACCOUNT_ID)
    }

    private suspend fun fetchAccountIdForAndroidAccount(): AccountId? {
        val account = IntentCompat.getParcelableExtra(intent, EXTRA_ACCOUNT, Account::class.java) ?: return null

        val accountType = account.type
        if (accountType != getString(R.string.account_type) && accountType != getString(R.string.account_type_db)) {
            logger.info("Unsupported account type: $accountType")
            return null
        }

        return androidAccountManager.getAccountId(account)
    }

    companion object {
        private const val EXTRA_ACCOUNT = "account"
        private const val EXTRA_ACCOUNT_ID = "accountId"

        fun createIntent(context: Context, accountId: AccountId): Intent {
            return Intent(context, AccountActivity::class.java).apply {
                AccountIdIntentSerializer.addExtra(this, EXTRA_ACCOUNT_ID, accountId)
            }
        }
        
        fun Intent.editAccountActivityIntent(accountId: AccountId) {
            AccountIdIntentSerializer.addExtra(this, EXTRA_ACCOUNT_ID, accountId)
        }
    }

}