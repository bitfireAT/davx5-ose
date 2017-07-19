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
import at.bitfire.davdroid.AccountSettings
import at.bitfire.davdroid.App
import at.bitfire.davdroid.resource.LocalAddressBook
import java.util.logging.Level

class ContactsSyncAdapterService: SyncAdapterService() {

    override fun syncAdapter() = ContactsSyncAdapter(this)


	protected class ContactsSyncAdapter(
            context: Context
    ): SyncAdapter(context) {

        override fun sync(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            try {
                val addressBook = LocalAddressBook(context, account, provider)

                val settings = AccountSettings(context, addressBook.getMainAccount())
                if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) && !checkSyncConditions(settings))
                    return

                App.log.info("Synchronizing address book: ${addressBook.getURL()}")
                App.log.info("Taking settings from: ${addressBook.getMainAccount()}")

                ContactsSyncManager(context, account, settings, extras, authority, syncResult, provider, addressBook)
                        .performSync()
            } catch(e: Exception) {
                App.log.log(Level.SEVERE, "Couldn't sync contacts", e)
            }

            App.log.info("Contacts sync complete")
        }

    }

}
