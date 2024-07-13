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
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.HttpClient
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

/**
 * Sync logic for address books
 */
class AddressBookSyncer @AssistedInject constructor(
    @ApplicationContext context: Context,
    private val contactsSyncManagerFactory: ContactsSyncManager.Factory,
    private val settingsManager: SettingsManager,
    db: AppDatabase,
    @Assisted account: Account,
    @Assisted extras: Array<String>,
    @Assisted authority: String,
    @Assisted syncResult: SyncResult,
): Syncer(context, db, account, extras, authority, syncResult) {

    @AssistedFactory
    interface Factory {
        fun create(account: Account, extras: Array<String>, authority: String, syncResult: SyncResult): AddressBookSyncer
    }

    companion object {
        const val PREVIOUS_GROUP_METHOD = "previous_group_method"
    }

    override fun sync() {

        // use contacts provider for address books (not address book authority)
        val contactsAuthority = ContactsContract.AUTHORITY

        // acquire ContentProviderClient
        val provider = try {
            context.contentResolver.acquireContentProviderClient(contactsAuthority)
        } catch (e: SecurityException) {
            Logger.log.log(Level.WARNING, "Missing permissions for authority $contactsAuthority", e)
            null
        }

        if (provider == null) {
            /* Can happen if
             - we're not allowed to access the content provider, or
             - the content provider is not available at all, for instance because the respective
               system app, like "contacts storage" is disabled */
            Logger.log.warning("Couldn't connect to content provider of authority $contactsAuthority")
            syncResult.stats.numParseExceptions++ // hard sync error
            return
        }

        // 1. find address book collections to be synced
        val remoteAddressBooks = mutableMapOf<HttpUrl, Collection>()
        val service = db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CARDDAV)
        if (service != null)
            for (collection in db.collectionDao().getByServiceAndSync(service.id))
                remoteAddressBooks[collection.url] = collection

        // permission check
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            if (remoteAddressBooks.isEmpty())
                Logger.log.info("No contacts permission, but no address book selected for synchronization")
            else
                Logger.log.warning("No contacts permission, but address books are selected for synchronization")
            return // Don't sync
        }

        // 2. update/delete local address books
        val forceAllReadOnly = settingsManager.getBoolean(Settings.FORCE_READ_ONLY_ADDRESSBOOKS)
        for (addressBook in LocalAddressBook.findAll(context, provider, account)) {
            val url = addressBook.url.toHttpUrl()
            val collection = remoteAddressBooks[url]
            if (collection == null) {
                Logger.log.log(Level.INFO, "Deleting obsolete local address book", url)
                addressBook.delete()
            } else {
                // remote CollectionInfo found for this local collection, update data
                try {
                    Logger.log.log(Level.FINE, "Updating local address book $url", collection)
                    addressBook.update(collection, forceAllReadOnly)
                } catch (e: Exception) {
                    Logger.log.log(Level.WARNING, "Couldn't rename address book account", e)
                }
                // we already have a local address book for this remote collection, don't take into consideration anymore
                remoteAddressBooks -= url
            }
        }

        // 3. create new local address books
        for ((_, info) in remoteAddressBooks) {
            Logger.log.log(Level.INFO, "Adding local address book", info)
            LocalAddressBook.create(context, provider, account, info, forceAllReadOnly)
        }

        // 4. sync local address books
        for (addressBook in LocalAddressBook.findAll(context, null, account)) {
            Logger.log.info("Synchronizing address book $addressBook")

            val url = addressBook.url.toHttpUrl()
            remoteAddressBooks[url]?.let { collection ->
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
            val accountSettings = AccountSettings(context, account)
            val addressBook = LocalAddressBook(context, account, provider)

            // handle group method change
            val groupMethod = accountSettings.getGroupMethod().name
            accountSettings.accountManager.getUserData(account, PREVIOUS_GROUP_METHOD)?.let { previousGroupMethod ->
                if (previousGroupMethod != groupMethod) {
                    Logger.log.info("Group method changed, deleting all local contacts/groups")

                    // delete all local contacts and groups so that they will be downloaded again
                    provider.delete(addressBook.syncAdapterURI(ContactsContract.RawContacts.CONTENT_URI), null, null)
                    provider.delete(addressBook.syncAdapterURI(ContactsContract.Groups.CONTENT_URI), null, null)

                    // reset sync state
                    addressBook.syncState = null
                }
            }
            accountSettings.accountManager.setAndVerifyUserData(account, PREVIOUS_GROUP_METHOD, groupMethod)

            Logger.log.info("Synchronizing address book: ${addressBook.url}")
            Logger.log.info("Taking settings from: ${addressBook.mainAccount}")

            val syncManager = contactsSyncManagerFactory.contactsSyncManager(account, accountSettings, httpClient.value, extras, authority, syncResult, provider, addressBook, collection)
            syncManager.performSync()

        } catch(e: Exception) {
            Logger.log.log(Level.SEVERE, "Couldn't sync contacts", e)
        }
        Logger.log.info("Contacts sync complete")
    }

}