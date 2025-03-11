/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.settings.migration

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.provider.ContactsContract
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.resource.LocalAddressBookStore
import at.bitfire.davdroid.sync.account.setAndVerifyUserData
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.use

/**
 * With DAVx5 4.4.3 address book account names now contain the collection ID as a unique
 * identifier. We need to update the address book account names.
 */
class AccountSettingsMigration17 @Inject constructor(
    private val collectionRepository: DavCollectionRepository,
    @ApplicationContext private val context: Context,
    private val localAddressBookFactory: LocalAddressBook.Factory,
    private val localAddressBookStore: LocalAddressBookStore,
    private val logger: Logger,
    private val serviceRepository: DavServiceRepository
): AccountSettingsMigration {

    override fun migrate(account: Account) {
        val addressBookAccountType = context.getString(R.string.account_type_address_book)
        try {
            context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)
        } catch (e: SecurityException) {
            // Not setting the collection ID will cause the address books to removed and fully re-synced as soon as there are permissions.
            logger.log(Level.WARNING, "Missing permissions for contacts authority, won't set collection ID for address books", e)
            null
        }?.use { provider ->
            val service = serviceRepository.getByAccountAndType(account.name, Service.TYPE_CARDDAV) ?: return

            val accountManager = AccountManager.get(context)
            // Get all old address books of this account, i.e. the ones which have a "real_account_name" of this account.
            // After this migration is run, address books won't be associated to accounts anymore but only to their respective collection/URL.
            val oldAddressBookAccounts = accountManager.getAccountsByType(addressBookAccountType)
                .filter { addressBookAccount ->
                    account.name == accountManager.getUserData(addressBookAccount, "real_account_name")
                }

            for (oldAddressBookAccount in oldAddressBookAccounts) {
                // Old address books only have a URL, so use it to determine the collection ID
                logger.info("Migrating address book ${oldAddressBookAccount.name}")
                val oldAddressBook = localAddressBookFactory.create(account, oldAddressBookAccount, provider)
                val url = accountManager.getUserData(oldAddressBookAccount, LOCAL_ADDRESS_BOOK_ACCOUNT_USER_DATA_URL)
                collectionRepository.getByServiceAndUrl(service.id, url)?.let { collection ->
                    // Set collection ID and rename the account
                    localAddressBookStore.update(provider, oldAddressBook, collection)
                    // The user-data-url is not being set in localAddressBookStore.update() anymore,
                    // but we need to keep it for the migration
                    accountManager.setAndVerifyUserData(
                        oldAddressBook.addressBookAccount,
                        LOCAL_ADDRESS_BOOK_ACCOUNT_USER_DATA_URL,
                        collection.url.toString()
                    )
                }
            }
        }
    }

    companion object {
        private const val LOCAL_ADDRESS_BOOK_ACCOUNT_USER_DATA_URL = "url"
    }

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class AccountSettingsMigrationModule {
        @Binds @IntoMap
        @IntKey(17)
        abstract fun provide(impl: AccountSettingsMigration17): AccountSettingsMigration
    }

}