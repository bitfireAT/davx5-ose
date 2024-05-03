/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.ui.account

import AccountScreen
import android.accounts.Account
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.components.ActivityComponent

@AndroidEntryPoint
class AccountActivity : AppCompatActivity() {

    @EntryPoint
    @InstallIn(ActivityComponent::class)
    interface AccountScreenEntryPoint {
        fun accountModelAssistedFactory(): AccountScreenModel.Factory
    }

    companion object {
        const val EXTRA_ACCOUNT = "account"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val account = intent.getParcelableExtra(EXTRA_ACCOUNT) as? Account
            ?: throw IllegalArgumentException("AccountActivity requires EXTRA_ACCOUNT")

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

}