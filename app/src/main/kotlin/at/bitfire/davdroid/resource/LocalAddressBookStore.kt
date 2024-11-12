/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import android.provider.ContactsContract
import androidx.annotation.VisibleForTesting
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.resource.LocalAddressBook.Companion.USER_DATA_COLLECTION_ID
import at.bitfire.davdroid.resource.LocalAddressBook.Companion.USER_DATA_URL
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.sync.account.SystemAccountUtils
import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.davdroid.util.setAndVerifyUserData
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.collections.orEmpty

class LocalAddressBookStore @Inject constructor(
    val addressBookFactory: LocalAddressBook.Factory,
    val collectionRepository: DavCollectionRepository,
    @ApplicationContext val context: Context,
    val localAddressBookFactory: LocalAddressBook.Factory,
    val logger: Logger,
    val serviceRepository: DavServiceRepository,
    val settings: SettingsManager
    ): LocalDataStore<LocalAddressBook> {

    /** whether a (usually managed) setting wants all address-books to be read-only **/
    val forceAllReadOnly: Boolean
        get() = settings.getBoolean(Settings.FORCE_READ_ONLY_ADDRESSBOOKS)


    /**
     * Assembles a name for the address book (account) from its corresponding database [Collection].
     *
     * The address book account name contains
     *
     * - the collection display name or last URL path segment
     * - the actual account name
     * - the collection ID, to make it unique.
     *
     * @param info  Collection to take info from
     */
    fun accountName(info: Collection): String {
        // Name the address book after given collection display name, otherwise use last URL path segment
        val sb = StringBuilder(info.displayName.let {
            if (it.isNullOrEmpty())
                info.url.lastSegment
            else
                it
        })
        // Add the actual account name to the address book account name
        serviceRepository.get(info.serviceId)?.let { service ->
            sb.append(" (${service.accountName})")
        }
        // Add the collection ID for uniqueness
        sb.append(" #${info.id}")
        return sb.toString()
    }


    override fun create(provider: ContentProviderClient, fromCollection: Collection): LocalAddressBook? {
        val name = accountName(fromCollection)
        val account = createAccount(
            name = name,
            id = fromCollection.id,
            url = fromCollection.url.toString()
        ) ?: return null

        val addressBook = addressBookFactory.create(account, provider)

        // update settings
        addressBook.updateSyncFrameworkSettings()
        addressBook.settings = contactsProviderSettings
        addressBook.readOnly = shouldBeReadOnly(fromCollection, forceAllReadOnly)

        return addressBook
    }

    fun createAccount(name: String, id: Long, url: String): Account? {
        // create account with collection ID and URL
        val account = Account(name, context.getString(R.string.account_type_address_book))
        val userData = Bundle(2).apply {
            putString(USER_DATA_COLLECTION_ID, id.toString())
            putString(USER_DATA_URL, url)
        }
        if (!SystemAccountUtils.createAccount(context, account, userData)) {
            logger.warning("Couldn't create address book account: $account")
            return null
        }

        return account
    }


    override fun getAll(account: Account, provider: ContentProviderClient): List<LocalAddressBook> =
        serviceRepository.getByAccountAndType(account.name, Service.TYPE_CARDDAV)?.let { service ->
            // get all collections for the CardDAV service
            collectionRepository.getByService(service.id).mapNotNull { collection ->
                // and map to a LocalAddressBook, if applicable
                findByCollection(provider, collection.id)
            }
        }.orEmpty()

    /**
     * Finds a [LocalAddressBook] based on its corresponding collection.
     *
     * @param id    collection ID to look for
     *
     * @return The [LocalAddressBook] for the given collection or *null* if not found
     */
    private fun findByCollection(provider: ContentProviderClient, id: Long): LocalAddressBook? {
        val accountManager = AccountManager.get(context)
        return accountManager
            .getAccountsByType(context.getString(R.string.account_type_address_book))
            .filter { account ->
                accountManager.getUserData(account, USER_DATA_COLLECTION_ID)?.toLongOrNull() == id
            }
            .map { account -> localAddressBookFactory.create(account, provider) }
            .firstOrNull()
    }


    override fun update(provider: ContentProviderClient, localCollection: LocalAddressBook, fromCollection: Collection) {
        var currentAccount = localCollection.addressBookAccount
        logger.log(Level.INFO, "Updating local address book $currentAccount from collection $fromCollection")

        // Update the account name
        val newAccountName = accountName(fromCollection)
        if (currentAccount.name != newAccountName) {
            // rename, move contacts/groups and update [AndroidAddressBook.]account
            localCollection.renameAccount(newAccountName)
            currentAccount.name = newAccountName
        }

        // Update the account user data
        val accountManager = AccountManager.get(context)
        accountManager.setAndVerifyUserData(currentAccount, USER_DATA_COLLECTION_ID, fromCollection.id.toString())
        accountManager.setAndVerifyUserData(currentAccount, USER_DATA_URL, fromCollection.url.toString())

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
            accountManager.getUserData(account, USER_DATA_COLLECTION_ID)?.toLongOrNull() == id
        }
        if (addressBookAccount != null)
            accountManager.removeAccountExplicitly(addressBookAccount)
    }


    companion object {

        /**
         * Contacts Provider Settings (equal for every address book)
         */
        val contactsProviderSettings
            get() = ContentValues(2).apply {
                // SHOULD_SYNC is just a hint that an account's contacts (the contacts of this local address book) are syncable.
                put(ContactsContract.Settings.SHOULD_SYNC, 1)

                // UNGROUPED_VISIBLE is required for making contacts work over Bluetooth (especially with some car systems).
                put(ContactsContract.Settings.UNGROUPED_VISIBLE, 1)
            }

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