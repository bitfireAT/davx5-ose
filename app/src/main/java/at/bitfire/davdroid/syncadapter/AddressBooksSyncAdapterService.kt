/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.accounts.AccountManager
import android.content.*
import android.database.DatabaseUtils
import android.os.Bundle
import android.provider.ContactsContract
import at.bitfire.davdroid.AccountSettings
import at.bitfire.davdroid.Logger
import at.bitfire.davdroid.R
import at.bitfire.davdroid.model.CollectionInfo
import at.bitfire.davdroid.model.ServiceDB
import at.bitfire.davdroid.model.ServiceDB.Collections
import at.bitfire.davdroid.resource.LocalAddressBook
import at.bitfire.vcard4android.ContactsStorageException
import java.util.logging.Level

class AddressBooksSyncAdapterService: SyncAdapterService() {

    override fun syncAdapter() = AddressBooksSyncAdapter(this)


	protected class AddressBooksSyncAdapter(
            context: Context
    ): SyncAdapter(context) {

        override fun sync(account: Account, extras: Bundle, authority: String, addressBooksProvider: ContentProviderClient, syncResult: SyncResult) {
            val contactsProvider = context.contentResolver.acquireContentProviderClient(ContactsContract.AUTHORITY)
            if (contactsProvider == null) {
                Logger.log.severe("Couldn't access contacts provider")
                syncResult.databaseError = true
                return
            }

            try {
                val settings = AccountSettings(context, account)

                /* don't run sync if
                   - sync conditions (e.g. "sync only in WiFi") are not met AND
                   - this is is an automatic sync (i.e. manual syncs are run regardless of sync conditions)
                 */
                if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) && !checkSyncConditions(settings))
                    return

                updateLocalAddressBooks(contactsProvider, account)

                val accountManager = AccountManager.get(context)
                for (addressBookAccount in accountManager.getAccountsByType(context.getString(R.string.account_type_address_book))) {
                    Logger.log.log(Level.INFO, "Running sync for address book", addressBookAccount)
                    val syncExtras = Bundle(extras)
                    syncExtras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true)
                    syncExtras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_BACKOFF, true)
                    ContentResolver.requestSync(addressBookAccount, ContactsContract.AUTHORITY, syncExtras)
                }
            } catch(e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't sync address books", e)
            }

            Logger.log.info("Address book sync complete")
        }

        private fun updateLocalAddressBooks(provider: ContentProviderClient, account: Account) {
            ServiceDB.OpenHelper(context).use { dbHelper ->
                val db = dbHelper.readableDatabase

                fun getService() =
                        db.query(ServiceDB.Services._TABLE, arrayOf(ServiceDB.Services.ID),
                            "${ServiceDB.Services.ACCOUNT_NAME}=? AND ${ServiceDB.Services.SERVICE}=?",
                            arrayOf(account.name, ServiceDB.Services.SERVICE_CARDDAV), null, null, null)?.use { c ->
                            if (c.moveToNext())
                                c.getLong(0)
                            else
                                null
                        }

                fun remoteAddressBooks(service: Long?): MutableMap<String, CollectionInfo> {
                    val collections = mutableMapOf<String, CollectionInfo>()
                    service?.let {
                        db.query(Collections._TABLE, null,
                            Collections.SERVICE_ID + "=? AND " + Collections.SYNC, arrayOf(service.toString()), null, null, null)?.use { cursor ->
                            while (cursor.moveToNext()) {
                                val values = ContentValues()
                                DatabaseUtils.cursorRowToContentValues(cursor, values)
                                val info = CollectionInfo(values)
                                collections[info.url] = info
                            }
                        }
                    }
                    return collections
                }

                // enumerate remote and local address books
                val service = getService()
                val remote = remoteAddressBooks(service)

                // delete/update local address books
                for (addressBook in LocalAddressBook.find(context, provider, account)) {
                    val url = addressBook.getURL()
                    val info = remote[url]
                    if (info == null) {
                        Logger.log.log(Level.INFO, "Deleting obsolete local address book", url)
                        addressBook.delete()
                    } else {
                        // remote CollectionInfo found for this local collection, update data
                        try {
                            Logger.log.log(Level.FINE, "Updating local address book $url", info)
                            addressBook.update(info)
                        } catch(e: ContactsStorageException) {
                            Logger.log.log(Level.WARNING, "Couldn't rename address book account", e)
                        }
                        // we already have a local address book for this remote collection, don't take into consideration anymore
                        remote -= url
                    }
                }

                // create new local address books
                for ((_, info) in remote) {
                    Logger.log.log(Level.INFO, "Adding local address book", info)
                    LocalAddressBook.create(context, provider, account, info)
                }
            }
        }

    }

}
