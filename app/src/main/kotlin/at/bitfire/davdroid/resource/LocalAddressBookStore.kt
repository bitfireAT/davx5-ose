/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import androidx.annotation.VisibleForTesting
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.resource.LocalAddressBook.Companion.USER_DATA_COLLECTION_ID
import at.bitfire.davdroid.resource.LocalAddressBook.Companion.USER_DATA_URL
import at.bitfire.davdroid.resource.LocalAddressBook.Companion.accountName
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.sync.account.SystemAccountUtils
import at.bitfire.davdroid.util.setAndVerifyUserData
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

class LocalAddressBookStore @Inject constructor(
    val addressBookFactory: LocalAddressBook.Factory,
    @ApplicationContext val context: Context,
    val logger: Logger,
    val settings: SettingsManager
): LocalDataStore<LocalAddressBook> {

    /** whether a (usually managed) setting wants all address-books to be read-only **/
    val forceAllReadOnly: Boolean
        get() = settings.getBoolean(Settings.FORCE_READ_ONLY_ADDRESSBOOKS)


    override fun create(provider: ContentProviderClient, fromCollection: Collection): LocalAddressBook? {
        val name = LocalAddressBook.accountName(context, fromCollection)
        val account = createAccount(
            name = name,
            id = fromCollection.id,
            url = fromCollection.url.toString()
        ) ?: return null

        val addressBook = addressBookFactory.create(account, provider)

        // update settings
        addressBook.updateSyncFrameworkSettings()
        addressBook.settings = contactsProviderSettings
        addressBook.readOnly = forceAllReadOnly || fromCollection.readOnly()

        return addressBook
    }

    fun createAccount(name: String, id: Long, url: String): Account? {
        // create account
        val account = Account(name, context.getString(R.string.account_type_address_book))
        val userData = LocalAddressBook.initialUserData(
            url = url,
            collectionId = id.toString()
        )
        if (!SystemAccountUtils.createAccount(context, account, userData)) {
            logger.warning("Couldn't create address book account: $account")
            return null
        }

        return account
    }

    override fun update(provider: ContentProviderClient, localCollection: LocalAddressBook, fromCollection: Collection) {
        var currentAccount = localCollection.addressBookAccount
        logger.log(Level.INFO, "Updating local address book $currentAccount from collection $fromCollection")

        // Update the account name
        val newAccountName = accountName(context, fromCollection)
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
            logger.info("Address book now read-only = $nowReadOnly, updating contacts")

            // update address book itself
            localCollection.readOnly = nowReadOnly

            // update raw contacts
            val rawContactValues = ContentValues(1)
            rawContactValues.put(RawContacts.RAW_CONTACT_IS_READ_ONLY, if (nowReadOnly) 1 else 0)
            provider.update(localCollection.rawContactsSyncUri(), rawContactValues, null, null)

            // update data rows
            val dataValues = ContentValues(1)
            dataValues.put(ContactsContract.Data.IS_READ_ONLY, if (nowReadOnly) 1 else 0)
            provider.update(localCollection.syncAdapterURI(ContactsContract.Data.CONTENT_URI), dataValues, null, null)

            // update group rows
            val groupValues = ContentValues(1)
            groupValues.put(Groups.GROUP_IS_READ_ONLY, if (nowReadOnly) 1 else 0)
            provider.update(localCollection.groupsSyncUri(), groupValues, null, null)
        }


        // make sure it will still be synchronized when contacts are updated
        localCollection.updateSyncFrameworkSettings()
    }


    override fun delete(localCollection: LocalAddressBook) {
        val accountManager = AccountManager.get(context)
        accountManager.removeAccountExplicitly(localCollection.addressBookAccount)
    }


    companion object {

        /**
         * Contacts Provider Settings (equal for every address book)
         */
        val contactsProviderSettings = ContentValues(2).apply {
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