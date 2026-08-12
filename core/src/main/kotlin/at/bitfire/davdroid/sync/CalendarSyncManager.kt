/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync

import at.bitfire.dav4jvm.ktor.DavCalendar
import at.bitfire.davdroid.ProductIds
import at.bitfire.davdroid.R
import at.bitfire.davdroid.accounts.AccountId
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.resource.LocalCalendar
import at.bitfire.davdroid.resource.LocalEvent
import at.bitfire.davdroid.resource.LocalResource
import at.bitfire.davdroid.resource.remote.CalDavCollection
import at.bitfire.davdroid.resource.remote.WebDavCollection
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
import io.ktor.http.content.TextContent
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.component.VEvent
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import java.util.logging.Level

/**
 * Synchronization manager for CalDAV collections; handles events (VEVENT).
 */
class CalendarSyncManager @AssistedInject constructor(
    @Assisted accountId: AccountId,
    @Assisted httpClient: HttpClient,
    @Assisted syncResult: SyncResult,
    @Assisted override val localCollection: LocalCalendar,
    @Assisted collectionInfo: Collection,
    @Assisted override val remoteCollection: CalDavCollection,
    @Assisted resync: ResyncType?,
    @Assisted settings: SyncSettings,
    private val productIds: ProductIds
) : SyncManager<LocalEvent>(
    accountId,
    httpClient,
    SyncDataType.EVENTS,
    syncResult,
    collectionInfo,
    resync,
    settings
) {

    @AssistedFactory
    interface Factory {
        fun calendarSyncManager(
            accountId: AccountId,
            httpClient: HttpClient,
            syncResult: SyncResult,
            localCalendar: LocalCalendar,
            collectionInfo: Collection,
            remoteCollection: CalDavCollection,
            resync: ResyncType?,
            settings: SyncSettings
        ): CalendarSyncManager
    }


    override suspend fun prepare(): Boolean {
        // if there are dirty exceptions for events, mark their master events as dirty, too
        val recurringCalendar = localCollection.recurringCalendar
        recurringCalendar.processDeletedExceptions()
        recurringCalendar.processDirtyExceptions()

        // now find dirty events that have no instances and set them to deleted
        localCollection.androidCalendar.deleteDirtyEventsWithoutInstances()

        return true
    }

    override fun syncAlgorithm(capabilities: WebDavCollection.Capabilities) =
        if (settings.timeRangePastDays != null || !capabilities.canCollectionSync)
            SyncAlgorithm.PROPFIND_REPORT
        else
            SyncAlgorithm.COLLECTION_SYNC

    override fun generateUpload(resource: LocalEvent, capabilities: WebDavCollection.Capabilities): GeneratedResource {
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

    override suspend fun processDownload(result: WebDavCollection.MultiGetItem) {
        result.url.withExceptionContext {
            val fileName = requireNotNull(result.url.lastSegment) { "Event URL has no path segment: ${result.url}" }
            try {
                processICalendar(
                    fileName = fileName,
                    eTag = result.eTag,
                    scheduleTag = result.scheduleTag,
                    reader = StringReader(result.content)
                )
            } catch (e: InvalidResourceException) {
                logger.log(Level.WARNING, "Could not map event", e)
                notifyInvalidResource(e, fileName)
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
        settings.defaultAlarm?.let { minBefore ->
            logger.info("Adding default alarm ($minBefore min before) to $event")
            DefaultReminderBuilder(minBefore = minBefore).add(to = androidEvent)
        }

        // create/update local event in calendar provider
        val local = localCollection.findByName(fileName)
        if (local != null) {
            local.withExceptionContext {
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