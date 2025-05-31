/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.Context
import android.provider.ContactsContract
import androidx.annotation.OpenForTesting
import androidx.annotation.VisibleForTesting
import androidx.core.content.contentValuesOf
import androidx.core.os.bundleOf
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.sync.account.SystemAccountUtils
import at.bitfire.davdroid.sync.account.setAndVerifyUserData
import at.bitfire.davdroid.util.DavUtils.lastSegment
import com.google.common.base.CharMatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

class LocalAddressBookStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localAddressBookFactory: LocalAddressBook.Factory,
    private val logger: Logger,
    private val serviceRepository: DavServiceRepository,
    private val settings: SettingsManager
): LocalDataStore<LocalAddressBook> {

    override val authority: String
        get() = ContactsContract.AUTHORITY

    /** whether a (usually managed) setting wants all address-books to be read-only **/
    val forceAllReadOnly: Boolean
        get() = settings.getBoolean(Settings.FORCE_READ_ONLY_ADDRESSBOOKS)


    /**
     * Assembles a name for the address book (account) from its corresponding database [Collection].
     *
     * The address book account name contains
     *
     * - the collection display name or last URL path segment (filtered for dangerous special characters)
     * - the actual account name
     * - the collection ID, to make it unique.
     *
     * @param info  Collection to take info from
     */
    fun accountName(info: Collection): String {
        // Name of address book is given collection display name, otherwise the last URL path segment
        var name = info.displayName.takeIf { !it.isNullOrEmpty() } ?: info.url.lastSegment

        // Remove ISO control characters + SQL problematic characters
        name = CharMatcher
            .javaIsoControl()
            .or(CharMatcher.anyOf("`'\""))
            .removeFrom(name)

        // Add the actual account name to the address book account name
        val sb = StringBuilder(name)
        serviceRepository.getBlocking(info.serviceId)?.let { service ->
            sb.append(" (${service.accountName})")
        }
        // Add the collection ID for uniqueness
        sb.append(" #${info.id}")
        return sb.toString()
    }

    override fun acquireContentProvider() =
        context.contentResolver.acquireContentProviderClient(authority)

    override fun create(provider: ContentProviderClient, fromCollection: Collection): LocalAddressBook? {
        val service = serviceRepository.getBlocking(fromCollection.serviceId) ?: throw IllegalArgumentException("Couldn't fetch DB service from collection")
        val account = Account(service.accountName, context.getString(R.string.account_type))

        val name = accountName(fromCollection)
        val addressBookAccount = createAddressBookAccount(
            account = account,
            name = name,
            id = fromCollection.id
        ) ?: return null

        val addressBook = localAddressBookFactory.create(account, addressBookAccount, provider)

        // update settings
        addressBook.updateSyncFrameworkSettings()
        addressBook.settings = contactsProviderSettings
        addressBook.readOnly = shouldBeReadOnly(fromCollection, forceAllReadOnly)

        return addressBook
    }

    @OpenForTesting
    internal fun createAddressBookAccount(account: Account, name: String, id: Long): Account? {
        // create address book account with reference to account, collection ID and URL
        val addressBookAccount = Account(name, context.getString(R.string.account_type_address_book))
        val userData = bundleOf(
            LocalAddressBook.USER_DATA_ACCOUNT_NAME to account.name,
            LocalAddressBook.USER_DATA_ACCOUNT_TYPE to account.type,
            LocalAddressBook.USER_DATA_COLLECTION_ID to id.toString()
        )
        if (!SystemAccountUtils.createAccount(context, addressBookAccount, userData)) {
            logger.warning("Couldn't create address book account: $addressBookAccount")
            return null
        }

        return addressBookAccount
    }

    override fun getAll(account: Account, provider: ContentProviderClient): List<LocalAddressBook> {
        val accountManager = AccountManager.get(context)
        return accountManager.getAccountsByType(context.getString(R.string.account_type_address_book))
            .filter { addressBookAccount ->
                accountManager.getUserData(addressBookAccount, LocalAddressBook.USER_DATA_ACCOUNT_NAME) == account.name &&
                accountManager.getUserData(addressBookAccount, LocalAddressBook.USER_DATA_ACCOUNT_TYPE) == account.type
            }
            .map { addressBookAccount ->
                localAddressBookFactory.create(account, addressBookAccount, provider)
            }
    }

    override fun update(provider: ContentProviderClient, localCollection: LocalAddressBook, fromCollection: Collection) {
        var currentAccount = localCollection.addressBookAccount
        logger.log(Level.INFO, "Updating local address book $currentAccount from collection $fromCollection")

        // Update the account name
        val newAccountName = accountName(fromCollection)
        if (currentAccount.name != newAccountName) {
            // rename, move contacts/groups and update [AndroidAddressBook.]account
            localCollection.renameAccount(newAccountName)
            currentAccount = Account(newAccountName, currentAccount.type)
        }

        // Update the account user data
        val accountManager = AccountManager.get(context)
        accountManager.setAndVerifyUserData(currentAccount, LocalAddressBook.USER_DATA_ACCOUNT_NAME, localCollection.account.name)
        accountManager.setAndVerifyUserData(currentAccount, LocalAddressBook.USER_DATA_ACCOUNT_TYPE, localCollection.account.type)
        accountManager.setAndVerifyUserData(currentAccount, LocalAddressBook.USER_DATA_COLLECTION_ID, fromCollection.id.toString())

        // Set contacts provider settings
        localCollection.settings = contactsProviderSettings

        // Update force read only
        val nowReadOnly = shouldBeReadOnly(fromCollection, forceAllReadOnly)
        if (nowReadOnly != localCollection.readOnly) {
            logger.info("Address book has changed to read-only = $nowReadOnly")
            localCollection.readOnly = nowReadOnly
        }

        // make sure it will still be synchronized when contacts are updated
        localCollection.updateSyncFrameworkSettings()
    }

    /**
     * Updates address books which are assigned to [oldAccount] so that they're assigned to [newAccount] instead.
     *
     * @param oldAccount    The old account
     * @param newAccount    The new account
     */
    override fun updateAccount(oldAccount: Account, newAccount: Account) {
        val accountManager = AccountManager.get(context)
        accountManager.getAccountsByType(context.getString(R.string.account_type_address_book))
            .filter { addressBookAccount ->
                accountManager.getUserData(addressBookAccount, LocalAddressBook.USER_DATA_ACCOUNT_NAME) == oldAccount.name &&
                accountManager.getUserData(addressBookAccount, LocalAddressBook.USER_DATA_ACCOUNT_TYPE) == oldAccount.type
            }
            .forEach { addressBookAccount ->
                accountManager.setAndVerifyUserData(addressBookAccount, LocalAddressBook.USER_DATA_ACCOUNT_NAME, newAccount.name)
                accountManager.setAndVerifyUserData(addressBookAccount, LocalAddressBook.USER_DATA_ACCOUNT_TYPE, newAccount.type)
            }
    }

    override fun delete(localCollection: LocalAddressBook) {
        val accountManager = AccountManager.get(context)
        accountManager.removeAccountExplicitly(localCollection.addressBookAccount)
    }

    /**
     * Deletes a [LocalAddressBook] based on its corresponding database collection.
     *
     * @param id    [Collection.id] to look for
     */
    fun deleteByCollectionId(id: Long) {
        val accountManager = AccountManager.get(context)
        val addressBookAccount = accountManager.getAccountsByType(context.getString(R.string.account_type_address_book)).firstOrNull { account ->
            accountManager.getUserData(account, LocalAddressBook.USER_DATA_COLLECTION_ID)?.toLongOrNull() == id
        }
        if (addressBookAccount != null)
            accountManager.removeAccountExplicitly(addressBookAccount)
    }


    companion object {

        /**
         * Contacts Provider Settings (equal for every address book)
         */
        val contactsProviderSettings
            get() = contentValuesOf(
                // SHOULD_SYNC is just a hint that an account's contacts (the contacts of this local address book) are syncable.
                ContactsContract.Settings.SHOULD_SYNC to 1,

                // UNGROUPED_VISIBLE is required for making contacts work over Bluetooth (especially with some car systems).
                ContactsContract.Settings.UNGROUPED_VISIBLE to 1
            )

        /**
         * Determines whether the address book should be set to read-only.
         *
         * @param forceAllReadOnly  Whether (usually managed, app-wide) setting should overwrite local read-only information
         * @param info              Collection data to determine read-only status from (either user-set read-only flag or missing write privilege)
         */
        @VisibleForTesting
        internal fun shouldBeReadOnly(info: Collection, forceAllReadOnly: Boolean): Boolean =
            info.readOnly() || forceAllReadOnly

    }

}