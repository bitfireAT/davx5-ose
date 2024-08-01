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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
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
    override val LocalAddressBook.collectionUrl: HttpUrl
        get() = url.toHttpUrl()

    override fun localCollections(provider: ContentProviderClient): List<LocalAddressBook>
        = LocalAddressBook.findAll(context, provider, account)

    override fun localSyncCollections(provider: ContentProviderClient): List<LocalAddressBook>
        = localCollections(provider)

    override fun getSyncCollections(serviceId: Long): List<Collection> =
        collectionRepository.getByServiceAndSync(serviceId)

    override fun LocalAddressBook.deleteCollection() {
        logger.log(Level.INFO, "Deleting obsolete local address book", collectionUrl)
        delete()
    }

    override fun LocalAddressBook.updateCollection(remoteCollection: Collection) {
        try {
            logger.log(Level.FINE, "Updating local address book $collectionUrl", remoteCollection)
            update(remoteCollection, forceAllReadOnly)
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't rename address book account", e)
        }
    }

    override fun create(provider: ContentProviderClient, remoteCollection: Collection) {
        logger.log(Level.INFO, "Adding local address book", remoteCollection)
        LocalAddressBook.create(context, provider, account, remoteCollection, forceAllReadOnly)
    }

    override fun LocalAddressBook.syncCollection(provider: ContentProviderClient, remoteCollection: Collection) {
        logger.info("Synchronizing address book $this")
        syncAddressBook(
            account,
            extras,
            httpClient,
            provider,
            syncResult,
            remoteCollection
        )
    }

    private fun syncAddressBook(
        account: Account,
        extras: Array<String>,
        httpClient: Lazy<HttpClient>,
        provider: ContentProviderClient,
        syncResult: SyncResult,
        collection: Collection
    ) {
        try {
            val accountSettings = accountSettingsFactory.forAccount(account)
            val addressBook = LocalAddressBook(context, account, provider)

            // handle group method change
            val groupMethod = accountSettings.getGroupMethod().name
            accountSettings.accountManager.getUserData(account, PREVIOUS_GROUP_METHOD)?.let { previousGroupMethod ->
                if (previousGroupMethod != groupMethod) {
                    logger.info("Group method changed, deleting all local contacts/groups")

                    // delete all local contacts and groups so that they will be downloaded again
                    provider.delete(addressBook.syncAdapterURI(ContactsContract.RawContacts.CONTENT_URI), null, null)
                    provider.delete(addressBook.syncAdapterURI(ContactsContract.Groups.CONTENT_URI), null, null)

                    // reset sync state
                    addressBook.syncState = null
                }
            }
            accountSettings.accountManager.setAndVerifyUserData(account, PREVIOUS_GROUP_METHOD, groupMethod)

            logger.info("Synchronizing address book: ${addressBook.url}")
            logger.info("Taking settings from: ${addressBook.mainAccount}")

            val syncManager = contactsSyncManagerFactory.contactsSyncManager(account, accountSettings, httpClient.value, extras, authority, syncResult, provider, addressBook, collection)
            syncManager.performSync()

        } catch(e: Exception) {
            logger.log(Level.SEVERE, "Couldn't sync contacts", e)
        }

        logger.info("Contacts sync complete")
    }

}