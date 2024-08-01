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

    override fun syncCollection(provider: ContentProviderClient, localCollection: LocalCalendar, remoteCollection: Collection) {
        logger.info("Synchronizing calendar #${localCollection.id}, URL: ${localCollection.name}")

        val syncManager = calendarSyncManagerFactory.calendarSyncManager(
            account,
            accountSettings,
            extras,
            httpClient.value,
            authority,
            syncResult,
            localCollection,
            remoteCollection
        )
        syncManager.performSync()
    }

    override fun update(localCollection: LocalCalendar, remoteCollection: Collection) {
        logger.log(Level.FINE, "Updating local calendar ${remoteCollection.url}", remoteCollection)
        localCollection.update(remoteCollection, accountSettings.getManageCalendarColors())
    }

    override fun create(provider: ContentProviderClient, remoteCollection: Collection) {
        logger.log(Level.INFO, "Adding local calendar", remoteCollection)
        LocalCalendar.create(account, provider, remoteCollection)
    }

}