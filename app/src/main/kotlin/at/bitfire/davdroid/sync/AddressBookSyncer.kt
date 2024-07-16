/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.util.setAndVerifyUserData
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Sync logic for address books
 */
class AddressBookSyncer @AssistedInject constructor(
    @ApplicationContext context: Context,
    settingsManager: SettingsManager,
    serviceRepository: DavServiceRepository,
    collectionRepository: DavCollectionRepository,
    private val contactsSyncManagerFactory: ContactsSyncManager.Factory,
    @Assisted account: Account,
    @Assisted extras: Array<String>,
    @Assisted authority: String,
    @Assisted syncResult: SyncResult,
    private val logger: Logger
): Syncer(context, serviceRepository, collectionRepository, account, extras, authority, syncResult) {

    @AssistedFactory
    interface Factory {
        fun create(account: Account, extras: Array<String>, authority: String, syncResult: SyncResult): AddressBookSyncer
    }

    companion object {
        const val PREVIOUS_GROUP_METHOD = "previous_group_method"
    }

    private val forceAllReadOnly = settingsManager.getBoolean(Settings.FORCE_READ_ONLY_ADDRESSBOOKS)
    private val localAddressBooks = mutableMapOf<HttpUrl, LocalAddressBook>()

    override fun beforeSync() {
        // permission check
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            if (remoteCollections.isEmpty())
                logger.info("No contacts permission, but no address book selected for synchronization")
            else
                logger.warning("No contacts permission, but address books are selected for synchronization")
            return // Don't sync
        }

        // Find all task lists and sync-enabled task lists
        LocalAddressBook.findAll(context, provider, account)
            .forEach { localAddressBook ->
                localAddressBook.url.let { url ->
                    localAddressBooks[url.toHttpUrl()] = localAddressBook
                }
            }
    }

    override fun getServiceType(): String =
        Service.TYPE_CARDDAV

    override fun getLocalResourceUrls(): List<HttpUrl?> =
        localAddressBooks.keys.toList()

    override fun deleteLocalResource(url: HttpUrl?) {
        logger.log(Level.INFO, "Deleting obsolete local address book", url)
        localAddressBooks[url]?.delete()
    }

    override fun updateLocalResource(collection: Collection) {
        try {
            logger.log(Level.FINE, "Updating local address book ${collection.url}", collection)
            localAddressBooks[collection.url]?.update(collection, forceAllReadOnly)
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Couldn't rename address book account", e)
        }
    }

    override fun createLocalResource(collection: Collection) {
        logger.log(Level.INFO, "Adding local address book", collection)
        LocalAddressBook.create(context, provider, account, collection, forceAllReadOnly)
    }

    override fun getLocalSyncableResourceUrls(): List<HttpUrl?> =
        localAddressBooks.keys.toList()

    override fun syncLocalResource(collection: Collection) {
        val addressBook = localAddressBooks[collection.url]
            ?: return

        logger.info("Synchronizing address book $addressBook")

        syncAddressBook(
            addressBook.account,
            extras,
            ContactsContract.AUTHORITY,
            httpClient,
            provider,
            syncResult,
            collection
        )
    }

    override fun afterSync() {}

    private fun syncAddressBook(
        account: Account,
        extras: Array<String>,
        authority: String,
        httpClient: Lazy<HttpClient>,
        provider: ContentProviderClient,
        syncResult: SyncResult,
        collection: Collection
    ) {
        try {
            val accountSettings = AccountSettings(context, account)
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

        // close content provider client which is acquired above
        provider.close()

        logger.info("Contacts sync complete")
    }

}