/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.davdroid.syncadapter

import android.accounts.Account
import android.content.Context
import android.content.SyncResult
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
import at.bitfire.davdroid.db.SyncState
import at.bitfire.davdroid.log.Logger
import at.bitfire.davdroid.network.HttpClient
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.davdroid.resource.LocalEvent
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.util.DavUtils
import at.bitfire.ical4android.Event
import at.bitfire.ical4android.InvalidCalendarException
import at.bitfire.ical4android.util.DateUtils
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.property.Action
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.commons.io.FileUtils
import java.io.ByteArrayOutputStream
import java.io.Reader
import java.io.StringReader
import java.time.Duration
import java.time.ZonedDateTime
import java.util.logging.Level

/**
 * Synchronization manager for CalDAV collections; handles events (VEVENT)
 */
class CalendarSyncManager(
    context: Context,
    account: Account,
    accountSettings: AccountSettings,
    extras: Array<String>,
    httpClient: HttpClient,
    authority: String,
    syncResult: SyncResult,
    localCalendar: LocalCalendar
): SyncManager<LocalEvent, LocalCalendar, DavCalendar>(context, account, accountSettings, httpClient, extras, authority, syncResult, localCalendar) {

    override fun prepare(): Boolean {
        collectionURL = (localCollection.name ?: return false).toHttpUrlOrNull() ?: return false
        davCollection = DavCalendar(httpClient.okHttpClient, collectionURL)

        // if there are dirty exceptions for events, mark their master events as dirty, too
        localCollection.processDirtyExceptions()

        // now find dirty events that have no instances and set them to deleted
        localCollection.deleteDirtyEventsWithoutInstances()

        return true
    }

    override fun queryCapabilities(): SyncState? =
        remoteExceptionContext {
            var syncState: SyncState? = null
            it.propfind(0, MaxResourceSize.NAME, SupportedReportSet.NAME, GetCTag.NAME, SyncToken.NAME) { response, relation ->
                if (relation == Response.HrefRelation.SELF) {
                    response[MaxResourceSize::class.java]?.maxSize?.let { maxSize ->
                        Logger.log.info("Calendar accepts events up to ${FileUtils.byteCountToDisplaySize(maxSize)}")
                    }

                    response[SupportedReportSet::class.java]?.let { supported ->
                        hasCollectionSync = supported.reports.contains(SupportedReportSet.SYNC_COLLECTION)
                    }
                    syncState = syncState(response)
                }
            }

            Logger.log.info("Calendar supports Collection Sync: $hasCollectionSync")
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
                Logger.log.warning("Restoring locally deleted event (read-only calendar!)")
                localExceptionContext(event) { it.resetDeleted() }
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
                Logger.log.warning("Resetting locally modified event to ETag=null (read-only calendar!)")
                localExceptionContext(event) { it.clearDirty(null, null) }
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

    override fun generateUpload(resource: LocalEvent): RequestBody = localExceptionContext(resource) {
        val event = requireNotNull(resource.event)
        Logger.log.log(Level.FINE, "Preparing upload of event ${resource.fileName}", event)

        val os = ByteArrayOutputStream()
        event.write(os)

        os.toByteArray().toRequestBody(DavCalendar.MIME_ICALENDAR_UTF8)
    }

    override fun listAllRemote(callback: MultiResponseCallback) {
        // calculate time range limits
        val limitStart = accountSettings.getTimeRangePastDays()?.let { pastDays ->
            ZonedDateTime.now().minusDays(pastDays.toLong()).toInstant()
        }

        return remoteExceptionContext { remote ->
            Logger.log.info("Querying events since $limitStart")
            remote.calendarQuery(Component.VEVENT, limitStart, null, callback)
        }
    }

    override fun downloadRemote(bunch: List<HttpUrl>) {
        Logger.log.info("Downloading ${bunch.size} iCalendars: $bunch")
        remoteExceptionContext {
            it.multiget(bunch) { response, _ ->
                responseExceptionContext(response) {
                    if (!response.isSuccess()) {
                        Logger.log.warning("Received non-successful multiget response for ${response.href}")
                        return@responseExceptionContext
                    }

                    val eTag = response[GetETag::class.java]?.eTag
                            ?: throw DavException("Received multi-get response without ETag")
                    val scheduleTag = response[ScheduleTag::class.java]?.scheduleTag

                    val calendarData = response[CalendarData::class.java]
                    val iCal = calendarData?.iCalendar
                            ?: throw DavException("Received multi-get response without calendar data")

                    processVEvent(DavUtils.lastSegmentOfUrl(response.href), eTag, scheduleTag, StringReader(iCal))
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
            Logger.log.log(Level.SEVERE, "Received invalid iCalendar, ignoring", e)
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
                Logger.log.log(Level.FINE, "${event.uid}: Adding default alarm", alarm)
                event.alarms += alarm
            }

            // update local event, if it exists
            localExceptionContext(localCollection.findByName(fileName)) { local ->
                if (local != null) {
                    Logger.log.log(Level.INFO, "Updating $fileName in local calendar", event)
                    local.eTag = eTag
                    local.scheduleTag = scheduleTag
                    local.update(event)
                    syncResult.stats.numUpdates++
                } else {
                    Logger.log.log(Level.INFO, "Adding $fileName to local calendar", event)
                    localExceptionContext(LocalEvent(localCollection, event, fileName, eTag, scheduleTag, LocalResource.FLAG_REMOTELY_PRESENT)) {
                        it.add()
                    }
                    syncResult.stats.numInserts++
                }
            }
        } else
            Logger.log.info("Received VCALENDAR with not exactly one VEVENT with UID and without RECURRENCE-ID; ignoring $fileName")
    }

    override fun notifyInvalidResourceTitle(): String =
        context.getString(R.string.sync_invalid_event)

}
