/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.Context
import android.provider.ContactsContract
import at.bitfire.davdroid.R
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.sync.SyncFrameworkIntegration
import at.bitfire.vcard4android.GroupMethod
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.junit.Assert.assertTrue
import java.io.FileNotFoundException
import java.util.Optional
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Logger

class LocalTestAddressBook @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted("addressBook") addressBookAccount: Account,
    @Assisted provider: ContentProviderClient,
    @Assisted override val groupMethod: GroupMethod,
    accountSettingsFactory: AccountSettings.Factory,
    collectionRepository: DavCollectionRepository,
    @ApplicationContext private val context: Context,
    logger: Logger,
    serviceRepository: DavServiceRepository,
    syncFramework: SyncFrameworkIntegration
): LocalAddressBook(
    account = account,
    _addressBookAccount = addressBookAccount,
    provider = provider,
    accountSettingsFactory = accountSettingsFactory,
    collectionRepository = collectionRepository,
    context = context,
    dirtyVerifier = Optional.empty(),
    logger = logger,
    serviceRepository = serviceRepository,
    syncFramework = syncFramework
) {

    @AssistedFactory
    interface Factory {
        fun create(account: Account, @Assisted("addressBook") addressBookAccount: Account, provider: ContentProviderClient, groupMethod: GroupMethod): LocalTestAddressBook
    }

    override var readOnly: Boolean
        get() = false
        set(_) = throw NotImplementedError()


    fun clear() {
        for (contact in queryContacts(null, null))
            contact.delete()
        for (group in queryGroups(null, null))
            group.delete()
    }


    /**
     * Returns the dirty flag of the given contact.
     *
     * @return true if the contact is dirty, false otherwise
     *
     * @throws FileNotFoundException if the contact can't be found
     */
    fun isContactDirty(id: Long): Boolean {
        val uri = ContentUris.withAppendedId(rawContactsSyncUri(), id)
        provider!!.query(uri, arrayOf(ContactsContract.RawContacts.DIRTY), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst())
                return cursor.getInt(0) != 0
        }
        throw FileNotFoundException()
    }

    /**
     * Returns the dirty flag of the given contact group.
     *
     * @return true if the group is dirty, false otherwise
     *
     * @throws FileNotFoundException if the group can't be found
     */
    fun isGroupDirty(id: Long): Boolean {
        val uri = ContentUris.withAppendedId(groupsSyncUri(), id)
        provider!!.query(uri, arrayOf(ContactsContract.Groups.DIRTY), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst())
                return cursor.getInt(0) != 0
        }
        throw FileNotFoundException()
    }

    fun remove() {
        val accountManager = AccountManager.get(context)
        assertTrue(accountManager.removeAccountExplicitly(addressBookAccount))
    }


    companion object {

        @dagger.hilt.EntryPoint
        @InstallIn(SingletonComponent::class)
        interface EntryPoint {
            fun localTestAddressBookFactory(): Factory
        }

        val counter = AtomicInteger()

        /**
         * Creates a [at.bitfire.davdroid.resource.LocalTestAddressBook].
         *
         * Make sure to delete it with [at.bitfire.davdroid.resource.LocalTestAddressBook.remove] or [removeAll] after use.
         */
        fun create(context: Context, account: Account, provider: ContentProviderClient, groupMethod: GroupMethod = GroupMethod.GROUP_VCARDS): LocalTestAddressBook {
            // create new address book account
            val addressBookAccount = Account("Test Address Book ${counter.incrementAndGet()}", context.getString(R.string.account_type_address_book))
            val accountManager = AccountManager.get(context)
            assertTrue(accountManager.addAccountExplicitly(addressBookAccount, null, null))

            // return address book with this account
            val entryPoint = EntryPointAccessors.fromApplication<EntryPoint>(context)
            val factory = entryPoint.localTestAddressBookFactory()
            return factory.create(account, addressBookAccount, provider, groupMethod)
        }

        fun removeAll(context: Context) {
            val accountManager = AccountManager.get(context)
            for (account in accountManager.getAccountsByType(context.getString(R.string.account_type_address_book)))
                accountManager.removeAccountExplicitly(account)
        }

    }

}