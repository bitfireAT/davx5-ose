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
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.settings.Settings
import at.bitfire.davdroid.settings.SettingsManager
import at.bitfire.davdroid.util.setAndVerifyUserData
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.logging.Level

/**
 * Sync logic for address books
 */
class AddressBookSyncer(
    context: Context
) : Syncer(context) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AddressBooksSyncerEntryPoint {
        fun contactsSyncManagerFactory(): ContactsSyncManager.Factory
        fun settingsManager(): SettingsManager
    }

    companion object {
        const val PREVIOUS_GROUP_METHOD = "previous_group_method"
    }

    private val entryPoint = EntryPointAccessors.fromApplication<AddressBooksSyncerEntryPoint>(context)
    val settingsManager = entryPoint.settingsManager()


    override fun sync(
        account: Account,
        extras: Array<String>,
        authority: String,                      // address book authority (not contacts authority)
        httpClient: Lazy<HttpClient>,
        provider: ContentProviderClient,        // for noop address book provider (not for contacts provider)
        syncResult: SyncResult
    ) {
        if (updateLocalAddressBooks(account, syncResult)) {
            context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)?.use { contactsProvider ->
                for (addressBookAccount in LocalAddressBook.findAll(context, null, account).map { it.account }) {
                    Logger.log.info("Synchronizing address book $addressBookAccount")
                    syncAddresBook(
                        addressBookAccount,
                        extras,
                        ContactsContract.AUTHORITY,
                        httpClient,
                        contactsProvider,
                        syncResult
                    )
                }
            }
        }
    }

    private fun updateLocalAddressBooks(account: Account, syncResult: SyncResult): Boolean {
        val service = db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CARDDAV)

        val remoteAddressBooks = mutableMapOf<HttpUrl, Collection>()
        if (service != null)
            for (collection in db.collectionDao().getByServiceAndSync(service.id))
                remoteAddressBooks[collection.url] = collection

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            if (remoteAddressBooks.isEmpty())
                Logger.log.info("No contacts permission, but no address book selected for synchronization")
            else
                Logger.log.warning("No contacts permission, but address books are selected for synchronization")
            return false
        }

        context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY).use { contactsProvider ->
            if (contactsProvider == null) {
                Logger.log.severe("Couldn't access contacts provider")
                syncResult.databaseError = true
                return false
            }

            val forceAllReadOnly = settingsManager.getBoolean(Settings.FORCE_READ_ONLY_ADDRESSBOOKS)

            // delete/update local address books
            for (addressBook in LocalAddressBook.findAll(context, contactsProvider, account)) {
                val url = addressBook.url.toHttpUrl()
                val info = remoteAddressBooks[url]
                if (info == null) {
                    Logger.log.log(Level.INFO, "Deleting obsolete local address book", url)
                    addressBook.delete()
                } else {
                    // remote CollectionInfo found for this local collection, update data
                    try {
                        Logger.log.log(Level.FINE, "Updating local address book $url", info)
                        addressBook.update(info, forceAllReadOnly)
                    } catch (e: Exception) {
                        Logger.log.log(Level.WARNING, "Couldn't rename address book account", e)
                    }
                    // we already have a local address book for this remote collection, don't take into consideration anymore
                    remoteAddressBooks -= url
                }
            }

            // create new local address books
            for ((_, info) in remoteAddressBooks) {
                Logger.log.log(Level.INFO, "Adding local address book", info)
                LocalAddressBook.create(context, contactsProvider, account, info, forceAllReadOnly)
            }
        }

        return true
    }


    fun syncAddresBook(
        account: Account,
        extras: Array<String>,
        authority: String,
        httpClient: Lazy<HttpClient>,
        provider: ContentProviderClient,
        syncResult: SyncResult
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

            val syncManagerFactory = entryPoint.contactsSyncManagerFactory()
            val syncManager = syncManagerFactory.contactsSyncManager(account, accountSettings, httpClient.value, extras, authority, syncResult, provider, addressBook)
            syncManager.performSync()

        } catch(e: Exception) {
            Logger.log.log(Level.SEVERE, "Couldn't sync contacts", e)
        }
        Logger.log.info("Contacts sync complete")
    }

}