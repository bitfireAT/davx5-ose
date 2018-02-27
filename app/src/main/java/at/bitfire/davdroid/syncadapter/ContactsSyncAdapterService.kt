/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentResolver
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import android.provider.ContactsContract
import at.bitfire.davdroid.AccountSettings
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.davdroid.settings.ISettings
import java.util.logging.Level

class ContactsSyncAdapterService: SyncAdapterService() {

    companion object {
        val PREVIOUS_GROUP_METHOD = "previous_group_method"
    }

    override fun syncAdapter() = ContactsSyncAdapter(this)


	class ContactsSyncAdapter(
            context: Context
    ): SyncAdapter(context) {

        override fun sync(settings: ISettings, account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            try {
                val addressBook = LocalAddressBook(context, account, provider)
                val accountSettings = AccountSettings(context, settings, addressBook.mainAccount)

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

                if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) && !checkSyncConditions(accountSettings))
                    return

                Logger.log.info("Synchronizing address book: ${addressBook.url}")
                Logger.log.info("Taking settings from: ${addressBook.mainAccount}")

                ContactsSyncManager(context, settings, account, accountSettings, extras, authority, syncResult, provider, addressBook).use {
                    it.performSync()
                }
            } catch(e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't sync contacts", e)
            }
            Logger.log.info("Contacts sync complete")
        }

    }

}
