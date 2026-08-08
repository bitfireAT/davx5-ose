/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.content.ContentProviderClient
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.davdroid.resource.LocalCalendarStore
import at.bitfire.davdroid.resource.remote.CalDavCollection
import at.bitfire.synctools.storage.calendar.AndroidCalendarProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.logging.Level

/**
 * Sync logic for calendars
 */
class CalendarSyncer @AssistedInject constructor(
    @Assisted accountId: AccountId,
    @Assisted resync: ResyncType?,
    @Assisted syncResult: SyncResult,
    @Assisted settings: SyncSettings,
    calendarStore: LocalCalendarStore,
    private val calendarSyncManagerFactory: CalendarSyncManager.Factory
) : Syncer<LocalCalendarStore, LocalCalendar>(accountId, resync, syncResult, settings) {

    @AssistedFactory
    interface Factory {
        fun create(
            accountId: AccountId,
            resyncType: ResyncType?,
            syncResult: SyncResult,
            settings: SyncSettings
        ): CalendarSyncer
    }

    override val dataStore = calendarStore

    override val serviceType: String
        get() = Service.TYPE_CALDAV


    override fun prepare(provider: ContentProviderClient): Boolean {
        // Update colors
        val account = androidAccountManager.getAndroidAccount(accountId)
        val calendarProvider = AndroidCalendarProvider(account, provider)
        if (settings.eventColors)
            calendarProvider.provideCss3ColorIndices()
        else
            calendarProvider.removeColorIndices()
        return true
    }

    override fun getDbSyncCollections(serviceId: Long): List<Collection> =
        collectionRepository.getSyncCalendars(serviceId)

    override suspend fun syncCollection(
        provider: ContentProviderClient,
        localCollection: LocalCalendar,
        remoteCollectionInfo: Collection
    ) {
        logger.log(
            Level.INFO,
            "Synchronizing calendar #{0}, DB Collection ID: {1}, URL: {2}",
            arrayOf<Any?>(
                localCollection.androidCalendar.id,
                localCollection.dbCollectionId,
                localCollection.androidCalendar.name
            )
        )

        val syncManager = calendarSyncManagerFactory.calendarSyncManager(
            accountId = accountId,
            httpClient = httpClient,
            syncResult = syncResult,
            localCalendar = localCollection,
            collectionInfo = remoteCollectionInfo,
            remoteCollection = CalDavCollection(httpClient, remoteCollectionInfo.url),
            resync = resync,
            settings = settings
        )
        syncManager.performSync()
    }

}