/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.ContentProviderClient
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.davdroid.resource.LocalCalendarStore
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.ical4android.AndroidCalendar
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.runBlocking

/**
 * Sync logic for calendars
 */
class CalendarSyncer @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted extras: Array<String>,
    @Assisted syncResult: SyncResult,
    calendarStore: LocalCalendarStore,
    private val accountSettingsFactory: AccountSettings.Factory,
    private val calendarSyncManagerFactory: CalendarSyncManager.Factory
): Syncer<LocalCalendarStore, LocalCalendar>(account, extras, syncResult) {

    @AssistedFactory
    interface Factory {
        fun create(account: Account, extras: Array<String>, syncResult: SyncResult): CalendarSyncer
    }

    override val dataStore = calendarStore

    override val serviceType: String
        get() = Service.TYPE_CALDAV


    override fun prepare(provider: ContentProviderClient): Boolean {
        // Update colors
        val accountSettings = accountSettingsFactory.create(account)
        if (accountSettings.getEventColors())
            AndroidCalendar.insertColors(provider, account)
        else
            AndroidCalendar.removeColors(provider, account)
        return true
    }

    override fun getDbSyncCollections(serviceId: Long): List<Collection> =
        collectionRepository.getSyncCalendars(serviceId)

    override fun syncCollection(provider: ContentProviderClient, localCollection: LocalCalendar, remoteCollection: Collection) {
        logger.info("Synchronizing calendar #${localCollection.id}, DB Collection ID: ${localCollection.dbCollectionId}, URL: ${localCollection.name}")

        val syncManager = calendarSyncManagerFactory.calendarSyncManager(
            account,
            extras,
            httpClient.value,
            dataStore.authority,
            syncResult,
            localCollection,
            remoteCollection
        )
        runBlocking {
            syncManager.performSync()
        }
    }

}