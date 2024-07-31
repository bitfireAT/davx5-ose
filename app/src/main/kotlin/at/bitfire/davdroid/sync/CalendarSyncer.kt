/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.SyncResult
import android.provider.CalendarContract
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.ical4android.AndroidCalendar
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.logging.Level

/**
 * Sync logic for calendars
 */
class CalendarSyncer @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted extras: Array<String>,
    @Assisted syncResult: SyncResult,
    private val calendarSyncManagerFactory: CalendarSyncManager.Factory
): Syncer<LocalCalendar>(account, extras, syncResult) {

    @AssistedFactory
    interface Factory {
        fun create(account: Account, extras: Array<String>, syncResult: SyncResult): CalendarSyncer
    }

    override val serviceType: String
        get() = Service.TYPE_CALDAV
    override val authority: String
        get() = CalendarContract.AUTHORITY
    override val LocalCalendar.collectionUrl: HttpUrl?
        get() = name?.toHttpUrl()

    override fun localCollections(provider: ContentProviderClient): List<LocalCalendar>
        = AndroidCalendar.find(account, provider, LocalCalendar.Factory, null, null)

    override fun localSyncCollections(provider: ContentProviderClient): List<LocalCalendar>
        = AndroidCalendar.find(account, provider, LocalCalendar.Factory, "${CalendarContract.Calendars.SYNC_EVENTS}!=0", null)

    override fun prepare(provider: ContentProviderClient): Boolean {
        // Update colors
        if (accountSettings.getEventColors())
            AndroidCalendar.insertColors(provider, account)
        else
            AndroidCalendar.removeColors(provider, account)
        return true
    }

    override fun getSyncCollections(serviceId: Long): List<Collection> =
        collectionRepository.getSyncCalendars(serviceId)

    override fun LocalCalendar.deleteCollection() {
        logger.log(Level.INFO, "Deleting obsolete local calendar", name)
        delete()
    }

    override fun LocalCalendar.syncCollection(provider: ContentProviderClient, remoteCollection: Collection) {
        logger.info("Synchronizing calendar #$id, URL: $name")

        val syncManager = calendarSyncManagerFactory.calendarSyncManager(
            account,
            accountSettings,
            extras,
            httpClient.value,
            authority,
            syncResult,
            this,
            remoteCollection
        )
        syncManager.performSync()
    }

    override fun LocalCalendar.updateCollection(remoteCollection: Collection) {
        logger.log(Level.FINE, "Updating local calendar $collectionUrl", remoteCollection)
        update(remoteCollection, accountSettings.getManageCalendarColors())
    }

    override fun createCollection(provider: ContentProviderClient, remoteCollection: Collection) {
        logger.log(Level.INFO, "Adding local calendar", remoteCollection)
        LocalCalendar.create(account, provider, remoteCollection)
    }

}