/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.text.format.Formatter
import at.bitfire.dav4jvm.okhttp.DavCalendar
import at.bitfire.dav4jvm.okhttp.MultiResponseCallback
import at.bitfire.dav4jvm.okhttp.Response
import at.bitfire.dav4jvm.okhttp.exception.DavException
import at.bitfire.dav4jvm.property.caldav.CalendarData
import at.bitfire.dav4jvm.property.caldav.GetCTag
import at.bitfire.dav4jvm.property.caldav.MaxResourceSize
import at.bitfire.dav4jvm.property.caldav.ScheduleTag
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.SupportedReportSet
import at.bitfire.dav4jvm.property.webdav.SyncToken
import at.bitfire.davdroid.Constants
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.di.SyncDispatcher
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.davdroid.resource.LocalEvent
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.resource.SyncState
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.util.DavUtils
import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.synctools.exception.InvalidICalendarException
import at.bitfire.synctools.icalendar.CalendarUidSplitter
import at.bitfire.synctools.icalendar.ICalendarGenerator
import at.bitfire.synctools.icalendar.ICalendarParser
import at.bitfire.synctools.mapping.calendar.AndroidEventBuilder
import at.bitfire.synctools.mapping.calendar.AndroidEventHandler
import at.bitfire.synctools.mapping.calendar.DefaultProdIdGenerator
import at.bitfire.synctools.mapping.calendar.SequenceUpdater
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.component.VEvent
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import java.time.ZonedDateTime
import java.util.Optional
import java.util.logging.Level

/**
 * Synchronization manager for CalDAV collections; handles events (VEVENT).
 */
class CalendarSyncManager @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted httpClient: OkHttpClient,
    @Assisted syncResult: SyncResult,
    @Assisted localCalendar: LocalCalendar,
    @Assisted collection: Collection,
    @Assisted resync: ResyncType?,
    accountSettingsFactory: AccountSettings.Factory,
    @SyncDispatcher syncDispatcher: CoroutineDispatcher
): SyncManager<LocalEvent, LocalCalendar, DavCalendar>(
    account,
    httpClient,
    SyncDataType.EVENTS,
    syncResult,
    localCalendar,
    collection,
    resync,
    syncDispatcher
) {

    @AssistedFactory
    interface Factory {
        fun calendarSyncManager(
            account: Account,
            httpClient: OkHttpClient,
            syncResult: SyncResult,
            localCalendar: LocalCalendar,
            collection: Collection,
            resync: ResyncType?
        ): CalendarSyncManager
    }

    private val accountSettings = accountSettingsFactory.create(account)


    override fun prepare(): Boolean {
        davCollection = DavCalendar(httpClient, collection.url)

        // if there are dirty exceptions for events, mark their master events as dirty, too
        val recurringCalendar = localCollection.recurringCalendar
        recurringCalendar.processDeletedExceptions()
        recurringCalendar.processDirtyExceptions()

        // now find dirty events that have no instances and set them to deleted
        localCollection.androidCalendar.deleteDirtyEventsWithoutInstances()

        return true
    }

    override suspend fun queryCapabilities(): SyncState? =
        SyncException.wrapWithRemoteResourceSuspending(collection.url) {
            var syncState: SyncState? = null
            runInterruptible {
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
            }

            logger.info("Calendar supports Collection Sync: $hasCollectionSync")
            syncState
        }

    override fun syncAlgorithm() =
        if (accountSettings.getTimeRangePastDays() != null || !hasCollectionSync)
            SyncAlgorithm.PROPFIND_REPORT
        else
            SyncAlgorithm.COLLECTION_SYNC

    override suspend fun processLocallyDeleted(): Boolean {
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

    override suspend fun uploadDirty(): Boolean {
        var modified = false
        if (localCollection.readOnly) {
            for (event in localCollection.findDirty()) {
                logger.warning("Resetting locally modified event to ETag=null (read-only calendar!)")
                SyncException.wrapWithLocalResource(event) {
                    event.clearDirty(Optional.empty(), null, null)
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

    override fun generateUpload(resource: LocalEvent): GeneratedResource {
        val localEvent = resource.androidEvent
        logger.log(Level.FINE, "Preparing upload of event #${resource.id}", localEvent)

        // increase SEQUENCE of main event and remember value
        val updatedSequence = SequenceUpdater().increaseSequence(localEvent.main)

        // map Android event to iCalendar (also generates UID, if necessary)
        val handler = AndroidEventHandler(
            accountName = resource.recurringCalendar.calendar.account.name,
            prodIdGenerator = DefaultProdIdGenerator(Constants.iCalProdId)
        )
        val mappedEvents = handler.mapToVEvents(localEvent)

        // persist UID if it was generated
        if (mappedEvents.generatedUid)
            resource.updateUid(mappedEvents.uid)

        // generate iCalendar and convert to request body
        val iCalWriter = StringWriter()
        ICalendarGenerator().write(mappedEvents.associatedEvents, iCalWriter)
        val requestBody = iCalWriter.toString().toRequestBody(DavCalendar.MIME_ICALENDAR_UTF8)

        return GeneratedResource(
            suggestedFileName = DavUtils.fileNameFromUid(mappedEvents.uid, "ics"),
            requestBody = requestBody,
            onSuccessContext = GeneratedResource.OnSuccessContext(
                sequence = updatedSequence
            )
        )
    }

    override suspend fun listAllRemote(callback: MultiResponseCallback) {
        // calculate time range limits
        val limitStart = accountSettings.getTimeRangePastDays()?.let { pastDays ->
            ZonedDateTime.now().minusDays(pastDays.toLong()).toInstant()
        }

        return SyncException.wrapWithRemoteResourceSuspending(collection.url) {
            logger.info("Querying events since $limitStart")
            runInterruptible {
                davCollection.calendarQuery(Component.VEVENT, limitStart, null, callback)
            }
        }
    }

    override suspend fun downloadRemote(bunch: List<HttpUrl>) {
        logger.info("Downloading ${bunch.size} iCalendars: $bunch")
        SyncException.wrapWithRemoteResourceSuspending(collection.url) {
            runInterruptible {
                davCollection.multiget(bunch) { response, _ ->
                    /*
                     * Real-world servers may return:
                     *
                     * - unrelated resources
                     * - the collection itself
                     * - the requested resources, but with a different collection URL (for instance, `/cal/1.ics` instead of `/shared-cal/1.ics`).
                     *
                     * So we:
                     *
                     * - ignore unsuccessful responses,
                     * - ignore responses without requested calendar data (should also ignore collections and hopefully unrelated resources), and
                     * - take the last segment of the href as the file name and assume that it's in the requested collection.
                     */
                    SyncException.wrapWithRemoteResource(response.href) wrapResource@{
                        if (!response.isSuccess()) {
                            logger.warning("Ignoring non-successful multi-get response for ${response.href}")
                            return@wrapResource
                        }

                        val iCal = response[CalendarData::class.java]?.iCalendar
                        if (iCal == null) {
                            logger.warning("Ignoring multi-get response without calendar-data")
                            return@wrapResource
                        }

                        val eTag = response[GetETag::class.java]?.eTag
                            ?: throw DavException("Received multi-get response without ETag")
                        val scheduleTag = response[ScheduleTag::class.java]?.scheduleTag

                        processICalendar(
                            fileName = response.href.lastSegment,
                            eTag = eTag,
                            scheduleTag = scheduleTag,
                            reader = StringReader(iCal)
                        )
                    }
                }
            }
        }
    }

    override fun postProcess() {}


    // helpers

    private fun processICalendar(fileName: String, eTag: String, scheduleTag: String?, reader: Reader) {
        val calendar =
            try {
                ICalendarParser().parse(reader)
            } catch (e: InvalidICalendarException) {
                logger.log(Level.WARNING, "Received invalid iCalendar, ignoring", e)
                notifyInvalidResource(e, fileName)
                return
            }

        val uidsAndEvents = CalendarUidSplitter<VEvent>().associateByUid(calendar, Component.VEVENT)
        if (uidsAndEvents.size != 1) {
            logger.warning("Received iCalendar with not exactly one UID; ignoring $fileName")
            return
        }
        // Event: main VEVENT and potentially attached exceptions (further VEVENTs with RECURRENCE-ID)
        val event = uidsAndEvents.values.first()

        // map AssociatedEvents (VEVENTs) to EventAndExceptions (Android events)
        val androidEvent = AndroidEventBuilder(
            calendar = localCollection.androidCalendar,
            syncId = fileName,
            eTag = eTag,
            scheduleTag = scheduleTag,
            flags = LocalResource.FLAG_REMOTELY_PRESENT
        ).build(event)

        // add default reminder (if desired)
        accountSettings.getDefaultAlarm()?.let { minBefore ->
            logger.log(Level.INFO, "Adding default alarm ($minBefore min before)", event)
            DefaultReminderBuilder(minBefore = minBefore).add(to = androidEvent)
        }

        // create/update local event in calendar provider
        val local = localCollection.findByName(fileName)
        if (local != null) {
            SyncException.wrapWithLocalResource(local) {
                logger.log(Level.INFO, "Updating $fileName in local calendar", event)
                local.update(androidEvent)
            }
        } else {
            logger.log(Level.INFO, "Adding $fileName to local calendar", event)
            localCollection.add(androidEvent)
        }
    }

    override fun notifyInvalidResourceTitle(): String =
        context.getString(R.string.sync_invalid_event)

}