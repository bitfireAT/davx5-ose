/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.mapping

import at.bitfire.dav4jvm.ktor.DavCalendar
import at.bitfire.davdroid.ProductIds
import at.bitfire.davdroid.resource.LocalJtxObject
import at.bitfire.davdroid.resource.remote.WebDavCollection
import at.bitfire.davdroid.sync.GeneratedResource
import at.bitfire.davdroid.sync.ResourceMapper
import at.bitfire.davdroid.util.DavUtils
import at.bitfire.synctools.icalendar.ICalendarGenerator
import at.bitfire.synctools.mapping.jtx.JtxObjectHandler
import at.bitfire.synctools.mapping.jtx.handler.AndroidAttachmentFetcher
import io.ktor.http.content.TextContent
import net.fortuna.ical4j.model.property.ProdId
import java.io.StringWriter
import java.util.logging.Level
import java.util.logging.Logger
import javax.inject.Inject

/**
 * Maps [LocalJtxObject]s to iCalendar (VTODO/VJOURNAL) for upload.
 */
class JtxMapper @Inject constructor(
    private val logger: Logger,
    private val productIds: ProductIds
) : ResourceMapper<LocalJtxObject> {

    override fun generateUpload(
        resource: LocalJtxObject,
        capabilities: WebDavCollection.Capabilities
    ): GeneratedResource {
        val localJtxObject = resource.jtxObjectAndExceptions
        logger.log(Level.FINE, "Preparing upload of icalobject #{0}: {1}", arrayOf(resource.id, localJtxObject))

        // Map jtx object to iCalendar (also generates UID, if necessary)
        val handler = JtxObjectHandler(
            prodId = ProdId(productIds.iCalProdId),
            attachmentFetcher = AndroidAttachmentFetcher(
                client = resource.collection.client,
                account = resource.collection.account
            )
        )
        val mappedJtxObjects = handler.mapToCalendarComponents(localJtxObject)

        // Persist UID if it was generated
        if (mappedJtxObjects.generatedUid) {
            resource.updateUid(mappedJtxObjects.uid)
        }

        // generate iCalendar and convert to request body
        val iCalWriter = StringWriter()
        ICalendarGenerator().write(mappedJtxObjects.associatedComponents, iCalWriter)
        val outgoingContent = TextContent(
            text = iCalWriter.toString(),
            contentType = DavCalendar.MIME_ICALENDAR_UTF8
        )

        return GeneratedResource(
            suggestedFileName = DavUtils.fileNameFromUid(mappedJtxObjects.uid, "ics"),
            content = outgoingContent
        )
    }

}
