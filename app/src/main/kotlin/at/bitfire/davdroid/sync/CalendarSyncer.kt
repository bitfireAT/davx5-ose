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
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.ical4android.AndroidCalendar
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.logging.Level
import javax.inject.Inject

/**
 * Sync logic for calendars
 */
class CalendarSyncer @Inject constructor(
    accountSettingsFactory: AccountSettings.Factory,
    @ApplicationContext context: Context,
    db: AppDatabase,
    private val calendarSyncManagerFactory: CalendarSyncManager.Factory
): Syncer(accountSettingsFactory, context, db) {

    override fun sync(
        account: Account,
        extras: Array<String>,
        authority: String,
        httpClient: Lazy<HttpClient>,
        provider: ContentProviderClient,
        syncResult: SyncResult
    ) {

        // 0. preparations
        val accountSettings = accountSettingsFactory.forAccount(account)
        if (accountSettings.getEventColors())
            AndroidCalendar.insertColors(provider, account)
        else
            AndroidCalendar.removeColors(provider, account)

        // 1. find calendar collections to be synced
        val remoteCollections = mutableMapOf<HttpUrl, Collection>()
        val service = db.serviceDao().getByAccountAndType(account.name, Service.TYPE_CALDAV)
        if (service != null)
            for (collection in db.collectionDao().getSyncCalendars(service.id))
                remoteCollections[collection.url] = collection

        // 2. update/delete local calendars and determine new remote collections
        val newCollections = HashMap(remoteCollections)
        val updateColors = accountSettings.getManageCalendarColors()
        for (calendar in AndroidCalendar.find(account, provider, LocalCalendar.Factory, null, null))
            calendar.name?.let {
                val url = it.toHttpUrl()
                val collection = remoteCollections[url]
                if (collection == null) {
                    Logger.log.log(Level.INFO, "Deleting obsolete local calendar", url)
                    calendar.delete()
                } else {
                    // remote CollectionInfo found for this local collection, update data
                    Logger.log.log(Level.FINE, "Updating local calendar $url", collection)
                    calendar.update(collection, updateColors)
                    // we already have a local calendar for this remote collection, don't create a new local calendar
                    newCollections -= url
                }
            }

        // 3. create new local calendars
        for ((_, info) in newCollections) {
            Logger.log.log(Level.INFO, "Adding local calendar", info)
            LocalCalendar.create(account, provider, info)
        }

        // 4. sync local calendars
        val calendars = AndroidCalendar
            .find(account, provider, LocalCalendar.Factory, "${CalendarContract.Calendars.SYNC_EVENTS}!=0", null)
        for (calendar in calendars) {
            val url = calendar.name?.toHttpUrl()
            remoteCollections[url]?.let { collection ->
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