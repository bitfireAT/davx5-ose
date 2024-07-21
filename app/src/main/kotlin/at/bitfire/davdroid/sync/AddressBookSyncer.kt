/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderClient
import android.content.SyncResult
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.util.setAndVerifyUserData
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.logging.Level
import javax.inject.Inject

/**
 * Sync logic for address books
 */
class AddressBookSyncer @Inject constructor(
    private val contactsSyncManagerFactory: ContactsSyncManager.Factory,
    private val settingsManager: SettingsManager
) : Syncer() {

    companion object {
        const val PREVIOUS_GROUP_METHOD = "previous_group_method"
    }

    override fun sync(
        account: Account,
        extras: Array<String>,
        authority: String,                      // address book authority (not contacts authority)
        httpClient: Lazy<HttpClient>,
        provider: ContentProviderClient,        // for noop address book provider (not for contacts provider)
        syncResult: SyncResult
    ) {

        // 1. find address book collections to be synced
        val remoteCollections = mutableMapOf<HttpUrl, Collection>()
        val service = db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CARDDAV)
        if (service != null)
            for (collection in db.collectionDao().getByServiceAndSync(service.id))
                remoteCollections[collection.url] = collection

        // permission check
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            if (remoteCollections.isEmpty())
                logger.info("No contacts permission, but no address book selected for synchronization")
            else
                logger.warning("No contacts permission, but address books are selected for synchronization")
            return // Don't sync
        }

        context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY).use { contactsProvider ->
            if (contactsProvider == null) {
                logger.severe("Couldn't access contacts provider")
                syncResult.databaseError = true
                return // Don't sync
            }

            // 2. update/delete local address books and determine new remote collections
            val forceAllReadOnly = settingsManager.getBoolean(Settings.FORCE_READ_ONLY_ADDRESSBOOKS)
            val newCollections = HashMap(remoteCollections)
            for (addressBook in LocalAddressBook.findAll(context, contactsProvider, account)) {
                val url = addressBook.url.toHttpUrl()
                val collection = remoteCollections[url]
                if (collection == null) {
                    logger.log(Level.INFO, "Deleting obsolete local address book", url)
                    addressBook.delete()
                } else {
                    // remote CollectionInfo found for this local collection, update data
                    try {
                        logger.log(Level.FINE, "Updating local address book $url", collection)
                        addressBook.update(collection, forceAllReadOnly)
                    } catch (e: Exception) {
                        logger.log(Level.WARNING, "Couldn't rename address book account", e)
                    }
                    // we already have a local address book for this remote collection, don't create a new local address book
                    newCollections -= url
                }
            }

            // 3. create new local address books
            for ((_, info) in newCollections) {
                logger.log(Level.INFO, "Adding local address book", info)
                LocalAddressBook.create(context, contactsProvider, account, info, forceAllReadOnly)
            }
        }

        // 4. sync local address books
        context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)?.use { contactsProvider ->
            for (addressBook in LocalAddressBook.findAll(context, null, account)) {
                logger.info("Synchronizing address book $addressBook")

                val url = addressBook.url.toHttpUrl()
                remoteCollections[url]?.let { collection ->
                    syncAddressBook(
                        addressBook.account,
                        extras,
                        ContactsContract.AUTHORITY,
                        httpClient,
                        contactsProvider,
                        syncResult,
                        collection
                    )
                }
            }
        }
    }

    fun syncAddressBook(
        account: Account,
        extras: Array<String>,
        authority: String,
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