/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import android.accounts.Account
import android.text.format.Formatter
import at.bitfire.dav4jvm.ktor.DavCalendar
import at.bitfire.dav4jvm.ktor.MultiStatusItem
import at.bitfire.dav4jvm.ktor.exception.DavException
import at.bitfire.dav4jvm.ktor.filterResponses
import at.bitfire.dav4jvm.ktor.filterSelfResponse
import at.bitfire.dav4jvm.property.caldav.CalDAV
import at.bitfire.dav4jvm.property.caldav.CalendarData
import at.bitfire.dav4jvm.property.caldav.MaxResourceSize
import at.bitfire.dav4jvm.property.caldav.ScheduleTag
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.SupportedReportSet
import at.bitfire.dav4jvm.property.webdav.WebDAV
import at.bitfire.davdroid.ProductIds
import at.bitfire.davdroid.R
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.di.qualifier.IoDispatcher
import at.bitfire.davdroid.di.qualifier.SyncTransferSemaphore
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.davdroid.resource.LocalEvent
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.resource.SyncState
import at.bitfire.davdroid.settings.AccountSettings
import at.bitfire.davdroid.util.DavUtils
import at.bitfire.davdroid.util.DavUtils.lastSegment
import at.bitfire.synctools.exception.InvalidResourceException
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
import io.ktor.client.HttpClient
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.component.VEvent
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import java.time.ZonedDateTime
import java.util.logging.Level

/**
 * Synchronization manager for CalDAV collections; handles events (VEVENT).
 */
class CalendarSyncManager @AssistedInject constructor(
    @Assisted account: Account,
    @Assisted httpClient: HttpClient,
    @Assisted syncResult: SyncResult,
    @Assisted localCalendar: LocalCalendar,
    @Assisted collection: Collection,
    @Assisted resync: ResyncType?,
    accountSettingsFactory: AccountSettings.Factory,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
    private val productIds: ProductIds,
    @SyncTransferSemaphore syncTransferSemaphore: Semaphore
) : SyncManager<LocalEvent, LocalCalendar, DavCalendar>(
    account,
    httpClient,
    SyncDataType.EVENTS,
    syncResult,
    localCalendar,
    collection,
    resync,
    ioDispatcher,
    syncTransferSemaphore
) {

    @AssistedFactory
    interface Factory {
        fun calendarSyncManager(
            account: Account,
            httpClient: HttpClient,
            syncResult: SyncResult,
            localCalendar: LocalCalendar,
            collection: Collection,
            resync: ResyncType?
        ): CalendarSyncManager
    }

    private val accountSettings = accountSettingsFactory.create(account)


    override suspend fun prepare(): Boolean {
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
        SyncException.wrapWithRemoteResource(collection.url) {
            val response = davCollection.propfind(
                0,
                CalDAV.MaxResourceSize,
                WebDAV.SupportedReportSet,
                CalDAV.GetCTag,
                WebDAV.SyncToken
            ).filterSelfResponse()

            var syncState: SyncState? = null
            if (response != null) {
                response[MaxResourceSize::class.java]?.maxSize?.let { maxSize ->
                    logger.info("Calendar accepts events up to ${Formatter.formatFileSize(context, maxSize)}")
                }

                response[SupportedReportSet::class.java]?.let { supported ->
                    hasCollectionSync = supported.reports.contains(WebDAV.SyncCollection)
                }
                syncState = syncState(response)
            }

            logger.info("Calendar supports Collection Sync: $hasCollectionSync")
            syncState
        }

    override fun syncAlgorithm() =
        if (accountSettings.getTimeRangePastDays() != null || !hasCollectionSync)
            SyncAlgorithm.PROPFIND_REPORT
        else
            SyncAlgorithm.COLLECTION_SYNC

    override fun generateUpload(resource: LocalEvent): GeneratedResource {
        val localEvent = resource.androidEvent
        logger.log(Level.FINE, "Preparing upload of event #{0}: {1}", arrayOf(resource.id, localEvent))

        /* Increase SEQUENCE of main event in memory and remember new value.
        Will be written to provider later over onSuccessContext. */
        val updatedSequence = SequenceUpdater().increaseSequence(localEvent.main)

        // map Android event to iCalendar (also generates UID, if necessary)
        val handler = AndroidEventHandler(
            accountName = resource.recurringCalendar.calendar.account.name,
            prodIdGenerator = DefaultProdIdGenerator(productIds.iCalProdId)
        )
        val mappedEvents = handler.mapToVEvents(localEvent)

        // persist UID if it was generated
        if (mappedEvents.generatedUid)
            resource.updateUid(mappedEvents.uid)

        // generate iCalendar and convert to request body
        val iCalWriter = StringWriter()
        ICalendarGenerator().write(mappedEvents.associatedEvents, iCalWriter)
        val outgoingContent = TextContent(
            text = iCalWriter.toString(),
            contentType = DavCalendar.MIME_ICALENDAR_UTF8
        )

        return GeneratedResource(
            suggestedFileName = DavUtils.fileNameFromUid(mappedEvents.uid, "ics"),
            content = outgoingContent,
            onSuccessContext = GeneratedResource.OnSuccessContext(
                sequence = updatedSequence
            )
        )
    }

    override fun listAllRemote(): Flow<MultiStatusItem> = flow {
        // calculate time range limits
        val limitStart = accountSettings.getTimeRangePastDays()?.let { pastDays ->
            ZonedDateTime.now().minusDays(pastDays.toLong()).toInstant()
        }

        SyncException.wrapWithRemoteResource(collection.url) {
            logger.info("Querying events since $limitStart")
            emitAll(davCollection.calendarQuery(Component.VEVENT, limitStart, null))
        }
    }

    override suspend fun downloadRemote(bunch: List<Url>) {
        logger.info("Downloading ${bunch.size} iCalendars: $bunch")
        SyncException.wrapWithRemoteResource(collection.url) {
            davCollection.multiget(bunch).filterResponses().collect { response ->
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
                    val fileName = response.href.lastSegment

                    try {
                        processICalendar(
                            fileName = fileName,
                            eTag = eTag,
                            scheduleTag = scheduleTag,
                            reader = StringReader(iCal)
                        )
                    } catch (e: InvalidResourceException) {
                        logger.log(Level.WARNING, "Could not map event", e)
                        notifyInvalidResource(e, fileName)
                    }
                }
            }
        }
    }

    override suspend fun postProcess() {}


    // helpers

    private suspend fun processICalendar(fileName: String, eTag: String, scheduleTag: String?, reader: Reader) {
        val calendar = ICalendarParser().parse(reader)

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
            logger.info("Adding default alarm ($minBefore min before) to $event")
            DefaultReminderBuilder(minBefore = minBefore).add(to = androidEvent)
        }

        // create/update local event in calendar provider
        val local = localCollection.findByName(fileName)
        if (local != null) {
            SyncException.wrapWithLocalResource(local) {
                logger.info("Updating $fileName in local calendar: $event")
                local.update(androidEvent)
            }
        } else {
            logger.info("Adding $fileName to local calendar: $event")
            localCollection.add(androidEvent)
        }
    }

    override fun notifyInvalidResourceTitle(): String =
        context.getString(R.string.sync_invalid_event)

}