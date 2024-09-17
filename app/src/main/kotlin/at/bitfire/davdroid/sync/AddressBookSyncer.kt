/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.SyncResult
import android.provider.ContactsContract
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.util.setAndVerifyUserData
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.logging.Level

/**
 * Sync logic for address books
 */
class AddressBookSyncer @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted extras: Array<String>,
    @Assisted syncResult: SyncResult,
    private val contactsSyncManagerFactory: ContactsSyncManager.Factory,
    settingsManager: SettingsManager
): Syncer<LocalAddressBook>(account, extras, syncResult) {

    @AssistedFactory
    interface Factory {
        fun create(account: Account, extras: Array<String>, syncResult: SyncResult): AddressBookSyncer
    }

    companion object {
        const val PREVIOUS_GROUP_METHOD = "previous_group_method"
    }

    private val forceAllReadOnly = settingsManager.getBoolean(Settings.FORCE_READ_ONLY_ADDRESSBOOKS)

    override val serviceType: String
        get() = Service.TYPE_CARDDAV
    override val authority: String
        get() = ContactsContract.AUTHORITY // Address books use the contacts authority for sync


    // FIXME should return _all_ address books; otherwise address book accounts of unchecked address books will not be removed
    override fun localSyncCollections(provider: ContentProviderClient): List<LocalAddressBook> =
        serviceRepository.getByAccountAndType(account.name, serviceType)?.let { service ->
            getSyncCollections(service.id).mapNotNull { collection ->
                LocalAddressBook.findByCollection(context, provider, collection.id)
            }
        }.orEmpty()

    override fun getSyncCollections(serviceId: Long): List<Collection> =
        collectionRepository.getByServiceAndSync(serviceId)

    override fun update(localCollection: LocalAddressBook, remoteCollection: Collection) {
        try {
            logger.log(Level.FINE, "Updating local address book ${remoteCollection.url}", remoteCollection)
            localCollection.update(remoteCollection, forceAllReadOnly)
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't rename address book account", e)
        }
    }

    override fun create(provider: ContentProviderClient, remoteCollection: Collection): LocalAddressBook {
        logger.log(Level.INFO, "Adding local address book", remoteCollection)
        return LocalAddressBook.create(context, provider, remoteCollection, forceAllReadOnly)
    }

    override fun syncCollection(provider: ContentProviderClient, localCollection: LocalAddressBook, remoteCollection: Collection) {
        logger.info("Synchronizing address book $localCollection")
        syncAddressBook(
            account = account,
            addressBook = localCollection,
            extras = extras,
            httpClient = httpClient,
            provider = provider,
            syncResult = syncResult,
            collection = remoteCollection
        )
    }

    /**
     * Synchronizes an address book
     *
     * @param addressBook local address book
     * @param extras Sync specific instructions. IE [Syncer.SYNC_EXTRAS_FULL_RESYNC]
     * @param httpClient
     * @param provider Content provider to access android contacts
     * @param syncResult Stores hard and soft sync errors
     * @param collection The database collection associated with this address book
     */
    private fun syncAddressBook(
        account: Account,
        addressBook: LocalAddressBook,
        extras: Array<String>,
        httpClient: Lazy<HttpClient>,
        provider: ContentProviderClient,
        syncResult: SyncResult,
        collection: Collection
    ) {
        try {
            val accountSettings = accountSettingsFactory.create(account)

            // handle group method change
            val groupMethod = accountSettings.getGroupMethod().name
            accountSettings.accountManager.getUserData(addressBook.account, PREVIOUS_GROUP_METHOD)?.let { previousGroupMethod ->
                if (previousGroupMethod != groupMethod) {
                    logger.info("Group method changed, deleting all local contacts/groups")

                    // delete all local contacts and groups so that they will be downloaded again
                    provider.delete(addressBook.syncAdapterURI(ContactsContract.RawContacts.CONTENT_URI), null, null)
                    provider.delete(addressBook.syncAdapterURI(ContactsContract.Groups.CONTENT_URI), null, null)

                    // reset sync state
                    addressBook.syncState = null
                }
            }
            accountSettings.accountManager.setAndVerifyUserData(addressBook.account, PREVIOUS_GROUP_METHOD, groupMethod)

            logger.info("Synchronizing address book: ${addressBook.collectionUrl}")

            val syncManager = contactsSyncManagerFactory.contactsSyncManager(account, accountSettings, httpClient.value, extras, authority, syncResult, provider, addressBook, collection)
            syncManager.performSync()

        } catch(e: Exception) {
            logger.log(Level.SEVERE, "Couldn't sync contacts", e)
        }

        logger.info("Contacts sync complete")
    }

}