/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import android.provider.ContactsContract
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.settings.AccountSettings
import java.util.logging.Level

class ContactsSyncAdapterService: SyncAdapterService() {

    companion object {
        const val PREVIOUS_GROUP_METHOD = "previous_group_method"
    }

    override fun syncAdapter() = ContactsSyncAdapter(this, appDatabase)


	class ContactsSyncAdapter(
        context: Context,
        appDatabase: AppDatabase
    ) : SyncAdapter(context, appDatabase) {

        override fun sync(account: Account, extras: Bundle, authority: String, httpClient: Lazy<HttpClient>, provider: ContentProviderClient, syncResult: SyncResult) {
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
                accountSettings.accountManager.setUserData(account, PREVIOUS_GROUP_METHOD, groupMethod)

                /* don't run sync if
                   - sync conditions (e.g. "sync only in WiFi") are not met AND
                   - this is is an automatic sync (i.e. manual syncs are run regardless of sync conditions)
                 */
                if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) && !checkSyncConditions(accountSettings))
                    return

                Logger.log.info("Synchronizing address book: ${addressBook.url}")
                Logger.log.info("Taking settings from: ${addressBook.mainAccount}")

                ContactsSyncManager(context, account, accountSettings, httpClient.value, extras, authority, syncResult, provider, addressBook).let {
                    it.performSync()
                }
            } catch(e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't sync contacts", e)
            }
            Logger.log.info("Contacts sync complete")
        }

    }

}
