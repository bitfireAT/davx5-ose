/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.mapping

import at.bitfire.dav4jvm.ktor.DavCalendar
import at.bitfire.davdroid.ProductIds
import at.bitfire.davdroid.resource.LocalEvent
import at.bitfire.davdroid.resource.remote.WebDavCollection
import at.bitfire.davdroid.sync.GeneratedResource
import at.bitfire.davdroid.util.DavUtils
import at.bitfire.synctools.icalendar.ICalendarGenerator
import at.bitfire.synctools.mapping.calendar.AndroidEventHandler
import at.bitfire.synctools.mapping.calendar.DefaultProdIdGenerator
import at.bitfire.synctools.mapping.calendar.SequenceUpdater
import io.ktor.http.content.TextContent
import java.io.StringWriter
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Maps [LocalEvent]s to iCalendar (VEVENT) for upload.
 */
class EventMapper @Inject constructor(
    private val logger: Logger,
    private val productIds: ProductIds
) : ResourceMapper<LocalEvent> {

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

}
