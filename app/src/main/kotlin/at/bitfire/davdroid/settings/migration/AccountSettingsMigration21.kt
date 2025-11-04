/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import at.bitfire.davdroid.R
import at.bitfire.davdroid.resource.LocalAddressBookStore
import at.bitfire.davdroid.sync.SyncDataType
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
 * This migration cancels (once only) any possibly still wrongly pending address book and calendar
 * (+tasks) account syncs.
 */
class AccountSettingsMigration21 @Inject constructor(
    private val localAddressBookStore: LocalAddressBookStore,
    private val logger: Logger
): AccountSettingsMigration {

    /**
     * Cancel any possibly forever pending account syncs of the different authorities
     */
    override fun migrate(account: Account) {
        if (Build.VERSION.SDK_INT >= 34) {
            // Request new dummy syncs (yes, seems like this is needed)
            val extras = Bundle().apply {
                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
                putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            }

            // Request calendar and tasks syncs
            val possibleAuthorities = SyncDataType.EVENTS.possibleAuthorities() +
                    SyncDataType.TASKS.possibleAuthorities()
            for (authority in possibleAuthorities) {
                ContentResolver.requestSync(account, authority, extras)
                logger.info("Android 14+: Canceling all (possibly forever pending) sync adapter syncs for $authority and $account")
                ContentResolver.cancelSync(account, authority) // Ignores possibly set sync extras
            }

            // Request contacts sync (per address book account)
            val addressBookAccounts = localAddressBookStore.getAddressBookAccounts(account) + account
            for (addressBookAccount in addressBookAccounts) {
                ContentResolver.requestSync(addressBookAccount, ContactsContract.AUTHORITY, extras)
                logger.info("Android 14+: Canceling all (possibly forever pending) sync adapter syncs for $addressBookAccount")
                ContentResolver.cancelSync(addressBookAccount, ContactsContract.AUTHORITY) // Ignores possibly set sync extras
            }
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