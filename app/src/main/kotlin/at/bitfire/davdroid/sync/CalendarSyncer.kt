/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.provider.CalendarContract
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.ical4android.AndroidCalendar
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.logging.Level

/**
 * Sync logic for calendars
 */
class CalendarSyncer(context: Context): Syncer(context) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CalendarSyncerEntryPoint {
        fun calendarSyncManagerFactory(): CalendarSyncManager.Factory
    }

    private val entryPoint = EntryPointAccessors.fromApplication<CalendarSyncerEntryPoint>(context)


    override fun sync(
        account: Account,
        extras: Array<String>,
        authority: String,
        httpClient: Lazy<HttpClient>,
        provider: ContentProviderClient,
        syncResult: SyncResult
    ) {
        val accountSettings = AccountSettings(context, account)

        if (accountSettings.getEventColors())
            AndroidCalendar.insertColors(provider, account)
        else
            AndroidCalendar.removeColors(provider, account)

        // Sync remote collection info (DB) to local calendars (content provider)
        val remoteCalendars = mutableMapOf<HttpUrl, Collection>()
        val service = db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CALDAV)
        if (service != null)
            for (collection in db.collectionDao().getSyncCalendars(service.id)) {
                remoteCalendars[collection.url] = collection
            }

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

        // create new local calendars
        for ((_, info) in remoteCalendars) {
            Logger.log.log(Level.INFO, "Adding local calendar", info)
            LocalCalendar.create(account, provider, info)
        }

        // Sync local calendars
        val calendars = AndroidCalendar
            .find(account, provider, LocalCalendar.Factory, "${CalendarContract.Calendars.SYNC_EVENTS}!=0", null)
        for (calendar in calendars) {
            val url = calendar.name?.toHttpUrl()
            remoteCalendars[url]?.let { collection ->
                Logger.log.info("Synchronizing calendar #${calendar.id}, URL: ${calendar.name}")

                val syncManagerFactory = entryPoint.calendarSyncManagerFactory()
                val syncManager = syncManagerFactory.calendarSyncManager(
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