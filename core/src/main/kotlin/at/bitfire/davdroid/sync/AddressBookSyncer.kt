/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.AccountManager
import android.content.ContentProviderClient
import android.provider.ContactsContract
import at.bitfire.dav4jvm.ktor.DavAddressBook
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.resource.LocalAddressBookStore
import at.bitfire.synctools.storage.contacts.AddressContract.asSyncAdapter
import at.bitfire.synctools.util.setAndVerifyUserData
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.logging.Level

/**
 * Sync logic for address books
 */
class AddressBookSyncer @AssistedInject constructor(
    @Assisted accountId: AccountId,
    @Assisted resync: ResyncType?,
    @Assisted val syncFrameworkUpload: Boolean,
    @Assisted syncResult: SyncResult,
    @Assisted settings: SyncSettings,
    addressBookStore: LocalAddressBookStore,
    private val contactsSyncManagerFactory: ContactsSyncManager.Factory,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : Syncer<LocalAddressBookStore, LocalAddressBook>(accountId, resync, syncResult, settings) {

    @AssistedFactory
    interface Factory {
        fun create(
            accountId: AccountId,
            resyncType: ResyncType?,
            syncFrameworkUpload: Boolean,
            syncResult: SyncResult,
            settings: SyncSettings
        ): AddressBookSyncer
    }

    override val dataStore = addressBookStore

    override val serviceType: String
        get() = Service.TYPE_CARDDAV


    override fun getDbSyncCollections(serviceId: Long): List<Collection> =
        collectionRepository.getByServiceAndSync(serviceId)

    override suspend fun syncCollection(
        provider: ContentProviderClient,
        localCollection: LocalAddressBook,
        remoteCollection: Collection
    ) {
        logger.log(Level.INFO, "Synchronizing address book: {0}", arrayOf(localCollection.addressBookAccount.name))
        syncAddressBook(
            accountId = accountId,
            addressBook = localCollection,
            provideHttpClient = { httpClient },
            provider = provider,
            syncResult = syncResult,
            collection = remoteCollection
        )
    }

    /**
     * Synchronizes an address book
     *
     * @param addressBook       local address book
     * @param provideHttpClient returns HTTP client on demand (no need to close)
     * @param provider          content provider to access android contacts
     * @param syncResult        stores hard and soft sync errors
     * @param collection        the database collection associated with this address book
     */
    private suspend fun syncAddressBook(
        accountId: AccountId,
        addressBook: LocalAddressBook,
        provideHttpClient: () -> HttpClient,
        provider: ContentProviderClient,
        syncResult: SyncResult,
        collection: Collection
    ) {
        try {
            handleGroupMethodChange(addressBook, provider)

            val httpClient = provideHttpClient()
            val syncManager = contactsSyncManagerFactory.contactsSyncManager(
                accountId = accountId,
                httpClient = httpClient,
                syncResult = syncResult,
                provider = provider,
                localAddressBook = addressBook,
                collection = collection,
                davCollection = DavAddressBook(httpClient, collection.url),
                resync = resync,
                syncFrameworkUpload = syncFrameworkUpload,
                settings = settings
            )
            syncManager.performSync()

        } catch(e: Exception) {
            logger.log(Level.SEVERE, "Couldn't sync contacts", e)
        }

        logger.info("Contacts sync complete")
    }

    private suspend fun handleGroupMethodChange(
        addressBook: LocalAddressBook,
        provider: ContentProviderClient
    ) {
        withContext(ioDispatcher) {
            handleGroupMethodChangeBlocking(addressBook, provider)
        }
    }

    private fun handleGroupMethodChangeBlocking(
        addressBook: LocalAddressBook,
        provider: ContentProviderClient
    ) {
        val groupMethod = settings.groupMethod.name

        val accountManager = AccountManager.get(context)
        accountManager.getUserData(addressBook.addressBookAccount, PREVIOUS_GROUP_METHOD)?.let { previousGroupMethod ->
            if (previousGroupMethod != groupMethod) {
                logger.info("Group method changed, deleting all local contacts/groups")

                // delete all local contacts and groups so that they will be downloaded again
                provider.delete(
                    ContactsContract.RawContacts.CONTENT_URI.asSyncAdapter(addressBook.addressBookAccount),
                    null,
                    null
                )
                provider.delete(
                    ContactsContract.Groups.CONTENT_URI.asSyncAdapter(addressBook.addressBookAccount),
                    null,
                    null
                )

                // reset sync state
                addressBook.syncState = null
            }
        }
        accountManager.setAndVerifyUserData(addressBook.addressBookAccount, PREVIOUS_GROUP_METHOD, groupMethod)
    }


    companion object {

        const val PREVIOUS_GROUP_METHOD = "previous_group_method"

    }

}