/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.provider.CalendarContract
import at.bitfire.davdroid.db.AppDatabase
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.ical4android.AndroidCalendar
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.logging.Level

/**
 * Sync logic for calendars
 */
class CalendarSyncer @AssistedInject constructor(
    @ApplicationContext context: Context,
    db: AppDatabase,
    private val calendarSyncManagerFactory: CalendarSyncManager.Factory,
    @Assisted account: Account,
    @Assisted extras: Array<String>,
    @Assisted authority: String,
    @Assisted syncResult: SyncResult
): Syncer(context, db, account, extras, authority, syncResult) {

    @AssistedFactory
    interface Factory {
        fun create(account: Account, extras: Array<String>, authority: String, syncResult: SyncResult): CalendarSyncer
    }

    override fun sync() {

        // 0. preparations

        // acquire ContentProviderClient
        val provider = try {
            context.contentResolver.acquireContentProviderClient(authority)
        } catch (e: SecurityException) {
            Logger.log.log(Level.WARNING, "Missing permissions for authority $authority", e)
            null
        }

        if (provider == null) {
            /* Can happen if
             - we're not allowed to access the content provider, or
             - the content provider is not available at all, for instance because the respective
               system app, like "calendar storage" is disabled */
            Logger.log.warning("Couldn't connect to content provider of authority $authority")
            syncResult.stats.numParseExceptions++ // hard sync error
            return
        }

        // Update colors
        val accountSettings = AccountSettings(context, account)
        if (accountSettings.getEventColors())
            AndroidCalendar.insertColors(provider, account)
        else
            AndroidCalendar.removeColors(provider, account)

        // 1. find calendar collections to be synced
        val remoteCalendars = mutableMapOf<HttpUrl, Collection>()
        val service = db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CALDAV)
        if (service != null)
            for (collection in db.collectionDao().getSyncCalendars(service.id))
                remoteCalendars[collection.url] = collection

        // 2. update/delete local calendars
        val updateColors = accountSettings.getManageCalendarColors()
        for (calendar in AndroidCalendar.find(account, provider, LocalCalendar.Factory, null, null))
            calendar.name?.let {
                val url = it.toHttpUrl()
                val collection = remoteCalendars[url]
                if (collection == null) {
                    Logger.log.log(Level.INFO, "Deleting obsolete local calendar", url)
                    calendar.delete()
                } else {
                    // remote CollectionInfo found for this local collection, update data
                    Logger.log.log(Level.FINE, "Updating local calendar $url", collection)
                    calendar.update(collection, updateColors)
                    // we already have a local calendar for this remote collection, don't take into consideration anymore
                    remoteCalendars -= url
                }
            }

        // 3. create new local calendars
        for ((_, info) in remoteCalendars) {
            Logger.log.log(Level.INFO, "Adding local calendar", info)
            LocalCalendar.create(account, provider, info)
        }

        // 4. sync local calendars
        val calendars = AndroidCalendar
            .find(account, provider, LocalCalendar.Factory, "${CalendarContract.Calendars.SYNC_EVENTS}!=0", null)
        for (calendar in calendars) {
            val url = calendar.name?.toHttpUrl()
            remoteCalendars[url]?.let { collection ->
                Logger.log.info("Synchronizing calendar #${calendar.id}, URL: ${calendar.name}")

                val syncManager = calendarSyncManagerFactory.calendarSyncManager(
                    account,
                    accountSettings,
                    extras,
                    httpClient.value,
                    authority,
                    syncResult,
                    calendar,
                    collection
                )
                syncManager.performSync()
            }
        }

    }
}