/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.content.SyncRequest
import android.provider.ContactsContract
import at.bitfire.davdroid.R
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import javax.inject.Inject

/**
 * On Android 14+ the pending sync state of the Sync Adapter Framework is not handled correctly.
 * As a workaround we cancel incoming sync requests (clears pending flag) after enqueuing our own
 * sync worker (work manager). With version 4.5.3 we started cancelling pending syncs for DAVx5
 * accounts, but forgot to do that for address book accounts. With version 4.5.4 we also cancel
 * those, but only once contact data of an address book has been edited.
 *
 * This migration cancels (once only) any wrongly pending address book account syncs that might still lie
 * dormant.
 */
class AccountSettingsMigration21 @Inject constructor(
    private val context: Context
): AccountSettingsMigration {

    private val accountManager = AccountManager.get(context)

    override fun migrate(account: Account) {
        val addressBookAccountType = context.getString(R.string.account_type_address_book)
        accountManager.getAccountsByType(addressBookAccountType).forEach { addressBookAccount ->
            ContentResolver.cancelSync(
                SyncRequest.Builder()
                    .setSyncAdapter(addressBookAccount, ContactsContract.AUTHORITY)
                    .syncOnce()
                    .build()
            )
        }
    }

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(21)
        abstract fun provide(impl: AccountSettingsMigration21): AccountSettingsMigration
    }

}