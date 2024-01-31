/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.provider.ContactsContract
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.util.setAndVerifyUserData
import java.util.logging.Level

/**
 * Sync logic for contacts
 */
class ContactSyncer(context: Context): Syncer(context) {

    companion object {
        const val PREVIOUS_GROUP_METHOD = "previous_group_method"
    }

    override fun sync(
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

            ContactsSyncManager(context, account, accountSettings, httpClient.value, extras, authority, syncResult, provider, addressBook).performSync()
        } catch(e: Exception) {
            Logger.log.log(Level.SEVERE, "Couldn't sync contacts", e)
        }
        Logger.log.info("Contacts sync complete")
    }
}