/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.Context
import android.content.SyncResult
import android.provider.CalendarContract
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.Service
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
import java.util.logging.Logger

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
    @Assisted syncResult: SyncResult,
    private val logger: Logger
): Syncer<LocalCalendar>(context, serviceRepository, collectionRepository, account, extras, syncResult) {

    @AssistedFactory
    interface Factory {
        fun create(account: Account, extras: Array<String>, syncResult: SyncResult): CalendarSyncer
    }

    private var updateColors = accountSettings.getManageCalendarColors()

    override val serviceType: String
        get() = Service.TYPE_CALDAV
    override val authority: String
        get() = CalendarContract.AUTHORITY
    override val localCollections: List<LocalCalendar>
        get() = AndroidCalendar.find(account, provider, LocalCalendar.Factory, null, null)
    override val localSyncCollections: List<LocalCalendar>
        get() = AndroidCalendar.find(account, provider, LocalCalendar.Factory, "${CalendarContract.Calendars.SYNC_EVENTS}!=0", null)

    override fun beforeSync() {
        // Update colors
        if (accountSettings.getEventColors())
            AndroidCalendar.insertColors(provider, account)
        else
            AndroidCalendar.removeColors(provider, account)
    }

    override fun getUrl(localCollection: LocalCalendar): HttpUrl? =
        localCollection.name?.toHttpUrl()

    override fun delete(localCollection: LocalCalendar) {
        logger.log(Level.INFO, "Deleting obsolete local calendar", localCollection.name)
        localCollection.delete()
    }

    override fun syncCollection(localCollection: LocalCalendar, remoteCollection: Collection) {
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
        localCollection.update(remoteCollection, updateColors)
    }

    override fun create(remoteCollection: Collection) {
        logger.log(Level.INFO, "Adding local calendar", remoteCollection)
        LocalCalendar.create(account, provider, remoteCollection)
    }

    override fun afterSync() {}

}