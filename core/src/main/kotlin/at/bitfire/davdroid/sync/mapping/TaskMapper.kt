/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.sync.mapping

import at.bitfire.dav4jvm.ktor.DavCalendar
import at.bitfire.davdroid.ProductIds
import at.bitfire.davdroid.resource.LocalTask
import at.bitfire.davdroid.resource.LocalTaskList
import at.bitfire.davdroid.resource.remote.WebDavCollection
import at.bitfire.davdroid.sync.PendingLocalUpdate
import at.bitfire.davdroid.util.DavUtils
import at.bitfire.synctools.icalendar.ICalendarGenerator
import at.bitfire.synctools.mapping.tasks.DmfsTaskHandler
import at.bitfire.synctools.mapping.tasks.SequenceUpdater
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.ktor.http.content.TextContent
import net.fortuna.ical4j.model.property.ProdId
import java.io.StringWriter
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Maps [LocalTask]s to iCalendar (VTODO) for upload.
 */
class TaskMapper @AssistedInject constructor(
    @Assisted private val localCollection: LocalTaskList,
    private val logger: Logger,
    private val productIds: ProductIds
) : ResourceMapper<LocalTask> {

    @AssistedFactory
    interface Factory {
        fun create(localCollection: LocalTaskList): TaskMapper
    }

    override fun generateUpload(resource: LocalTask, capabilities: WebDavCollection.Capabilities): GeneratedResource {
        val localTask = resource.taskAndExceptions
        logger.log(Level.FINE, "Preparing upload of task #{0}: {1}", arrayOf(resource.id, localTask))

        /* Increase SEQUENCE of main task in memory and remember new value.
        Will be written to provider later via pendingLocalUpdate. */
        val updatedSequence = SequenceUpdater().increaseSequence(localTask.main)

        // map Android event to iCalendar (also generates UID, if necessary)
        val handler = DmfsTaskHandler(
            prodId = ProdId(productIds.iCalProdId),
            providerName = localCollection.dmfsTaskList.providerName
        )
        val mappedVToDos = handler.mapToVToDos(localTask)

        // persist UID if it was generated
        if (mappedVToDos.generatedUid)
            resource.updateUid(mappedVToDos.uid)

        // generate iCalendar and convert to request body
        val iCalWriter = StringWriter()
        ICalendarGenerator().write(mappedVToDos.associatedTasks, iCalWriter)
        val outgoingContent = TextContent(
            text = iCalWriter.toString(),
            contentType = DavCalendar.MIME_ICALENDAR_UTF8
        )

        return GeneratedResource(
            suggestedFileName = DavUtils.fileNameFromUid(mappedVToDos.uid, "ics"),
            content = outgoingContent,
            pendingLocalUpdate = PendingLocalUpdate(
                sequence = updatedSequence
            )
        )
    }

}
