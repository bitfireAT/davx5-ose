/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.content.SyncResult
import android.text.format.Formatter
import at.bitfire.dav4jvm.DavCalendar
import at.bitfire.dav4jvm.MultiResponseCallback
import at.bitfire.dav4jvm.Response
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.property.caldav.CalendarData
import at.bitfire.dav4jvm.property.caldav.GetCTag
import at.bitfire.dav4jvm.property.caldav.MaxResourceSize
import at.bitfire.dav4jvm.property.caldav.ScheduleTag
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.SupportedReportSet
import at.bitfire.dav4jvm.property.webdav.SyncToken
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.db.SyncState
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.davdroid.resource.LocalEvent
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.InvalidCalendarException
import at.bitfire.ical4android.util.DateUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.io.ByteArrayOutputStream
import java.io.Reader
import java.io.StringReader
import java.time.Duration
import java.time.ZonedDateTime
import java.util.logging.Level
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.property.Action
import okhttp3.HttpUrl
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Synchronization manager for CalDAV collections; handles events (VEVENT).
 */
class CalendarSyncManager @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted accountSettings: AccountSettings,
    @Assisted extras: Array<String>,
    @Assisted httpClient: HttpClient,
    @Assisted authority: String,
    @Assisted syncResult: SyncResult,
    @Assisted localCalendar: LocalCalendar,
    @Assisted collection: Collection
): SyncManager<LocalEvent, LocalCalendar, DavCalendar>(
    account,
    accountSettings,
    httpClient,
    extras,
    authority,
    syncResult,
    localCalendar,
    collection
) {

    @AssistedFactory
    interface Factory {
        fun calendarSyncManager(
            account: Account,
            accountSettings: AccountSettings,
            extras: Array<String>,
            httpClient: HttpClient,
            authority: String,
            syncResult: SyncResult,
            localCalendar: LocalCalendar,
            collection: Collection
        ): CalendarSyncManager
    }

    override fun prepare() {
        davCollection = DavCalendar(httpClient.okHttpClient, collection.url)

        // if there are dirty exceptions for events, mark their master events as dirty, too
        localCollection.processDirtyExceptions()

        // now find dirty events that have no instances and set them to deleted
        localCollection.deleteDirtyEventsWithoutInstances()
    }

    override fun queryCapabilities(): SyncState? =
        SyncException.wrapWithRemoteResource(collection.url) {
            var syncState: SyncState? = null
            davCollection.propfind(0, MaxResourceSize.NAME, SupportedReportSet.NAME, GetCTag.NAME, SyncToken.NAME) { response, relation ->
                if (relation == Response.HrefRelation.SELF) {
                    response[MaxResourceSize::class.java]?.maxSize?.let { maxSize ->
                        logger.info("Calendar accepts events up to ${Formatter.formatFileSize(context, maxSize)}")
                    }

                    response[SupportedReportSet::class.java]?.let { supported ->
                        hasCollectionSync = supported.reports.contains(SupportedReportSet.SYNC_COLLECTION)
                    }
                    syncState = syncState(response)
                }
            }

            logger.info("Calendar supports Collection Sync: $hasCollectionSync")
            syncState
        }

    override fun syncAlgorithm() =
        if (accountSettings.getTimeRangePastDays() != null || !hasCollectionSync)
            SyncAlgorithm.PROPFIND_REPORT
        else
            SyncAlgorithm.COLLECTION_SYNC

    override fun processLocallyDeleted(): Boolean {
        if (localCollection.readOnly) {
            var modified = false
            for (event in localCollection.findDeleted()) {
                logger.warning("Restoring locally deleted event (read-only calendar!)")
                SyncException.wrapWithLocalResource(event) {
                    event.resetDeleted()
                }
                modified = true
            }

            // This is unfortunately ugly: When an event has been inserted to a read-only calendar
            // it's not enough to force synchronization (by returning true),
            // but we also need to make sure all events are downloaded again.
            if (modified)
                localCollection.lastSyncState = null

            return modified
        }
        // mirror deletions to remote collection (DELETE)
        return super.processLocallyDeleted()
    }

    override fun uploadDirty(): Boolean {
        var modified = false
        if (localCollection.readOnly) {
            for (event in localCollection.findDirty()) {
                logger.warning("Resetting locally modified event to ETag=null (read-only calendar!)")
                SyncException.wrapWithLocalResource(event) {
                    event.clearDirty(null, null)
                }
                modified = true
            }

            // This is unfortunately ugly: When an event has been inserted to a read-only calendar
            // it's not enough to force synchronization (by returning true),
            // but we also need to make sure all events are downloaded again.
            if (modified)
                localCollection.lastSyncState = null
        }

        // generate UID/file name for newly created events
        val superModified = super.uploadDirty()

        // return true when any operation returned true
        return modified or superModified
    }

    override fun generateUpload(resource: LocalEvent): RequestBody =
        SyncException.wrapWithLocalResource(resource) {
            val event = requireNotNull(resource.event)
            logger.log(Level.FINE, "Preparing upload of event ${resource.fileName}", event)

            val os = ByteArrayOutputStream()
            event.write(os)

            os.toByteArray().toRequestBody(DavCalendar.MIME_ICALENDAR_UTF8)
        }

    override fun listAllRemote(callback: MultiResponseCallback) {
        // calculate time range limits
        val limitStart = accountSettings.getTimeRangePastDays()?.let { pastDays ->
            ZonedDateTime.now().minusDays(pastDays.toLong()).toInstant()
        }

        return SyncException.wrapWithRemoteResource(collection.url) {
            logger.info("Querying events since $limitStart")
            davCollection.calendarQuery(Component.VEVENT, limitStart, null, callback)
        }
    }

    override fun downloadRemote(bunch: List<HttpUrl>) {
        logger.info("Downloading ${bunch.size} iCalendars: $bunch")
        SyncException.wrapWithRemoteResource(collection.url) {
            davCollection.multiget(bunch) { response, _ ->
                SyncException.wrapWithRemoteResource(response.href) wrapResponse@ {
                    if (!response.isSuccess()) {
                        logger.warning("Received non-successful multiget response for ${response.href}")
                        return@wrapResponse
                    }

                    val eTag = response[GetETag::class.java]?.eTag
                            ?: throw DavException("Received multi-get response without ETag")
                    val scheduleTag = response[ScheduleTag::class.java]?.scheduleTag

                    val calendarData = response[CalendarData::class.java]
                    val iCal = calendarData?.iCalendar
                            ?: throw DavException("Received multi-get response without calendar data")

                    processVEvent(
                        response.href.lastSegment,
                        eTag,
                        scheduleTag,
                        StringReader(iCal)
                    )
                }
            }
        }
    }

    override fun postProcess() {}


    // helpers

    private fun processVEvent(fileName: String, eTag: String, scheduleTag: String?, reader: Reader) {
        val events: List<Event>
        try {
            events = Event.eventsFromReader(reader)
        } catch (e: InvalidCalendarException) {
            logger.log(Level.SEVERE, "Received invalid iCalendar, ignoring", e)
            notifyInvalidResource(e, fileName)
            return
        }

        if (events.size == 1) {
            val event = events.first()

            // set default reminder for non-full-day events, if requested
            val defaultAlarmMinBefore = accountSettings.getDefaultAlarm()
            if (defaultAlarmMinBefore != null && DateUtils.isDateTime(event.dtStart) && event.alarms.isEmpty()) {
                val alarm = VAlarm(Duration.ofMinutes(-defaultAlarmMinBefore.toLong())).apply {
                    // Sets METHOD_ALERT instead of METHOD_DEFAULT in the calendar provider.
                    // Needed for calendars to actually show a notification.
                    properties += Action.DISPLAY
                }
                logger.log(Level.FINE, "${event.uid}: Adding default alarm", alarm)
                event.alarms += alarm
            }

            // update local event, if it exists
            val local = localCollection.findByName(fileName)
            SyncException.wrapWithLocalResource(local) {
                if (local != null) {
                    logger.log(Level.INFO, "Updating $fileName in local calendar", event)
                    local.eTag = eTag
                    local.scheduleTag = scheduleTag
                    local.update(event)
                    syncResult.stats.numUpdates++
                } else {
                    logger.log(Level.INFO, "Adding $fileName to local calendar", event)
                    val newLocal = LocalEvent(localCollection, event, fileName, eTag, scheduleTag, LocalResource.FLAG_REMOTELY_PRESENT)
                    SyncException.wrapWithLocalResource(newLocal) {
                        newLocal.add()
                    }
                    syncResult.stats.numInserts++
                }
            }
        } else
            logger.info("Received VCALENDAR with not exactly one VEVENT with UID and without RECURRENCE-ID; ignoring $fileName")
    }

    override fun notifyInvalidResourceTitle(): String =
        context.getString(R.string.sync_invalid_event)

}