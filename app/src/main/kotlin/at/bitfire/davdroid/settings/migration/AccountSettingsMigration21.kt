/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import at.bitfire.davdroid.R
import at.bitfire.davdroid.sync.adapter.SyncFrameworkIntegration
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import java.util.logging.Logger
import javax.inject.Inject

/**
 * On Android 14+ the pending sync state of the Sync Adapter Framework is not handled correctly.
 * As a workaround we cancel incoming sync requests (clears pending flag) after enqueuing our own
 * sync worker (work manager). With version 4.5.3 we started cancelling pending syncs for DAVx5
 * accounts, but forgot to do that for address book accounts. With version 4.5.4 we also cancel
 * those, but only when contact data of an address book has been edited.
 *
 * This migration cancels (once only) any possibly still wrongly pending address book and calendar account syncs.
 */
class AccountSettingsMigration21 @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncFrameworkIntegration: SyncFrameworkIntegration,
    private val logger: Logger
): AccountSettingsMigration {

    private val accountManager = AccountManager.get(context)

    private val calendarAccountType = context.getString(R.string.account_type)
    private val addressBookAccountType = context.getString(R.string.account_type_address_book)

    override fun migrate(account: Account) {
        // Cancel the (after an update) possibly forever pending calendar account syncs
        cancelSyncsByAccountType(calendarAccountType)

        // Cancel the (after an update) possibly forever pending address book account syncs
        cancelSyncsByAccountType(addressBookAccountType)
    }

    /**
     * Cancels any (possibly forever pending) syncs for the accounts of given type.
     */
    private fun cancelSyncsByAccountType(accountType: String) {
        val authority = when (accountType) {
            calendarAccountType -> CalendarContract.AUTHORITY
            addressBookAccountType -> ContactsContract.AUTHORITY
            else -> return
        }
        accountManager.getAccountsByType(accountType).forEach { account ->
            logger.info("Canceling (possibly forever pending) sync for $account")
            syncFrameworkIntegration.cancelSync(account, authority, Bundle())
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