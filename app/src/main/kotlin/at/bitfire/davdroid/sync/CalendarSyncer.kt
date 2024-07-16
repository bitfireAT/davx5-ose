/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.Context
import android.content.SyncResult
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.repository.DavCollectionRepository
import at.bitfire.davdroid.repository.DavServiceRepository
import at.bitfire.davdroid.resource.LocalCalendar
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
    serviceRepository: DavServiceRepository,
    collectionRepository: DavCollectionRepository,
    private val calendarSyncManagerFactory: CalendarSyncManager.Factory,
    @Assisted account: Account,
    @Assisted extras: Array<String>,
    @Assisted authority: String,
    @Assisted syncResult: SyncResult
): Syncer(context, serviceRepository, collectionRepository, account, extras, authority, syncResult) {

    @AssistedFactory
    interface Factory {
        fun create(account: Account, extras: Array<String>, authority: String, syncResult: SyncResult): CalendarSyncer
    }

    private var updateColors = accountSettings.getManageCalendarColors()
    private val localCalendars = mutableMapOf<HttpUrl, LocalCalendar>()
    private val localSyncCalendars = mutableMapOf<HttpUrl, LocalCalendar>()

    override fun beforeSync() {

        // Update colors
        if (accountSettings.getEventColors())
            AndroidCalendar.insertColors(provider, account)
        else
            AndroidCalendar.removeColors(provider, account)

        // Find all calenders and sync-enabled calendars
        AndroidCalendar.find(account, provider, LocalCalendar.Factory, null, null)
            .forEach { localCalendar ->
                localCalendar.name?.let { url ->
                    localCalendars[url.toHttpUrl()] = localCalendar
                }
            }
        localCalendars.forEach { (url, localCalendar) ->
            if (localCalendar.isSynced)
                localSyncCalendars[url] = localCalendar
        }
    }

    override fun getServiceType(): String = Service.TYPE_CALDAV

    override fun getLocalResourceUrls() = localCalendars.keys.toList()

    override fun deleteLocalResource(url: HttpUrl?) {
        Logger.log.log(Level.INFO, "Deleting obsolete local calendar", url)
        localCalendars[url]?.delete()
    }

    override fun getLocalSyncableResourceUrls(): List<HttpUrl?> = localCalendars.keys.toList()

    override fun syncLocalResource(collection: Collection) {
        val calendar = localSyncCalendars[collection.url]
            ?: return

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

    override fun updateLocalResource(collection: Collection) {
        Logger.log.log(Level.FINE, "Updating local calendar ${collection.url}", collection)
        localCalendars[collection.url]?.update(collection, updateColors)
    }

    override fun createLocalResource(collection: Collection) {
        Logger.log.log(Level.INFO, "Adding local calendar", collection)
        LocalCalendar.create(account, provider, collection)
    }

    override fun afterSync() {}

}