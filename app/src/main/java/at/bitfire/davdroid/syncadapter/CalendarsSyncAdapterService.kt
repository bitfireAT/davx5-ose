/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.*
import android.database.DatabaseUtils
import android.os.Bundle
import android.provider.CalendarContract
import at.bitfire.davdroid.AccountSettings
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.model.CollectionInfo
import at.bitfire.davdroid.model.ServiceDB
import at.bitfire.davdroid.model.ServiceDB.Collections
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.ical4android.AndroidCalendar
import java.util.logging.Level

class CalendarsSyncAdapterService: SyncAdapterService() {

    override fun syncAdapter() = SyncAdapter(this)


	protected class SyncAdapter(
            context: Context
    ): SyncAdapterService.SyncAdapter(context) {

        override fun sync(account: Account, extras: Bundle, authority: String, provider: ContentProviderClient, syncResult: SyncResult) {
            try {
                val settings = AccountSettings(context, account)

                /* don't run sync if
                   - sync conditions (e.g. "sync only in WiFi") are not met AND
                   - this is is an automatic sync (i.e. manual syncs are run regardless of sync conditions)
                 */
                if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) && !checkSyncConditions(settings))
                    return

                if (settings.getEventColors())
                    AndroidCalendar.insertColors(provider, account)
                else
                    AndroidCalendar.removeColors(provider, account)

                updateLocalCalendars(provider, account, settings)

                for (calendar in AndroidCalendar.find(account, provider, LocalCalendar.Factory, "${CalendarContract.Calendars.SYNC_EVENTS}!=0", null)) {
                    Logger.log.info("Synchronizing calendar #${calendar.id}, URL: ${calendar.name}")
                    CalendarSyncManager(context, account, settings, extras, authority, syncResult, provider, calendar).use {
                        it.performSync()
                    }
                }
            } catch(e: Exception) {
                Logger.log.log(Level.SEVERE, "Couldn't sync calendars", e)
            }

            Logger.log.info("Calendar sync complete")
        }

        private fun updateLocalCalendars(provider: ContentProviderClient, account: Account, settings: AccountSettings) {
            ServiceDB.OpenHelper(context).use { dbHelper ->
                val db = dbHelper.readableDatabase

                fun getService() =
                        db.query(ServiceDB.Services._TABLE, arrayOf(ServiceDB.Services.ID),
                                "${ServiceDB.Services.ACCOUNT_NAME}=? AND ${ServiceDB.Services.SERVICE}=?",
                                arrayOf(account.name, ServiceDB.Services.SERVICE_CALDAV), null, null, null)?.use { c ->
                            if (c.moveToNext())
                                c.getLong(0)
                            else
                                null
                        }

                fun remoteCalendars(service: Long?): MutableMap<String, CollectionInfo> {
                    val collections = mutableMapOf<String, CollectionInfo>()
                    service?.let {
                        db.query(Collections._TABLE, null,
                                "${Collections.SERVICE_ID}=? AND ${Collections.SUPPORTS_VEVENT}!=0 AND ${Collections.SYNC}",
                                arrayOf(service.toString()), null, null, null).use { cursor ->
                            while (cursor.moveToNext()) {
                                val values = ContentValues(cursor.columnCount)
                                DatabaseUtils.cursorRowToContentValues(cursor, values)
                                val info = CollectionInfo(values)
                                collections[info.url] = info
                            }
                        }
                    }
                    return collections
                }

                // enumerate remote and local calendars
                val service = getService()
                val remote = remoteCalendars(service)

                // delete/update local calendars
                val updateColors = settings.getManageCalendarColors()

                for (calendar in AndroidCalendar.find(account, provider, LocalCalendar.Factory, null, null))
                    calendar.name?.let { url ->
                        val info = remote[url]
                        if (info == null) {
                            Logger.log.log(Level.INFO, "Deleting obsolete local calendar", url)
                            calendar.delete()
                        } else {
                            // remote CollectionInfo found for this local collection, update data
                            Logger.log.log(Level.FINE, "Updating local calendar $url", info)
                            calendar.update(info, updateColors)
                            // we already have a local calendar for this remote collection, don't take into consideration anymore
                            remote -= url
                        }
                    }

                // create new local calendars
                for ((_, info) in remote) {
                    Logger.log.log(Level.INFO, "Adding local calendar", info)
                    LocalCalendar.create(account, provider, info)
                }
            }
        }

    }

}
