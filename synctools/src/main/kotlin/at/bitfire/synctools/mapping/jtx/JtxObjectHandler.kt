/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx

import android.content.Entity
import at.bitfire.synctools.icalendar.AssociatedComponents
import at.bitfire.synctools.mapping.jtx.handler.AttachmentFetcher
import at.bitfire.synctools.mapping.jtx.handler.AttachmentsHandler
import at.bitfire.synctools.mapping.jtx.handler.AttendeesHandler
import at.bitfire.synctools.mapping.jtx.handler.CategoriesHandler
import at.bitfire.synctools.mapping.jtx.handler.ClassificationHandler
import at.bitfire.synctools.mapping.jtx.handler.ColorHandler
import at.bitfire.synctools.mapping.jtx.handler.CommentsHandler
import at.bitfire.synctools.mapping.jtx.handler.CompletedHandler
import at.bitfire.synctools.mapping.jtx.handler.ContactHandler
import at.bitfire.synctools.mapping.jtx.handler.CreatedHandler
import at.bitfire.synctools.mapping.jtx.handler.DescriptionHandler
import at.bitfire.synctools.mapping.jtx.handler.GeoHandler
import at.bitfire.synctools.mapping.jtx.handler.JtxObjectEntityHandler
import at.bitfire.synctools.mapping.jtx.handler.LastModifiedHandler
import at.bitfire.synctools.mapping.jtx.handler.LocationHandler
import at.bitfire.synctools.mapping.jtx.handler.OrganizerHandler
import at.bitfire.synctools.mapping.jtx.handler.PercentCompleteHandler
import at.bitfire.synctools.mapping.jtx.handler.PriorityHandler
import at.bitfire.synctools.mapping.jtx.handler.RecurrenceFieldsHandler
import at.bitfire.synctools.mapping.jtx.handler.RelatedToHandler
import at.bitfire.synctools.mapping.jtx.handler.ResourcesHandler
import at.bitfire.synctools.mapping.jtx.handler.SequenceHandler
import at.bitfire.synctools.mapping.jtx.handler.StatusHandler
import at.bitfire.synctools.mapping.jtx.handler.SummaryHandler
import at.bitfire.synctools.mapping.jtx.handler.TimeFieldsHandler
import at.bitfire.synctools.mapping.jtx.handler.UidHandler
import at.bitfire.synctools.mapping.jtx.handler.UnknownPropertiesHandler
import at.bitfire.synctools.mapping.jtx.handler.UrlHandler
import at.bitfire.synctools.mapping.jtx.handler.ExtendedStatusHandler
import at.bitfire.synctools.storage.jtx.JtxObjectAndExceptions
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RRule
import java.util.UUID

/**
 * Mapper from jtx Board object main + data rows to [VJournal] or [VToDo].
 *
 * @param prodId the `PRODID` to use
 * @param attachmentFetcher An implementation of [AttachmentFetcher] to fetch attachments from the JTX database
 */
class JtxObjectHandler(
    private val prodId: ProdId,
    attachmentFetcher: AttachmentFetcher
) {

    /* Note: the storage layer (JtxCollection) doesn't read/write all sub-rows,
    but only those defined in JtxCollection.SUB_VALUE_URIS – so all sub-rows
    that are supported by builders/handlers should also be present there. */

    private val entityHandlers: Array<JtxObjectEntityHandler> = arrayOf(
        AttachmentsHandler(attachmentFetcher),
        AttendeesHandler(),
        CategoriesHandler(),
        ClassificationHandler(),
        ColorHandler(),
        CommentsHandler(),
        CompletedHandler(),
        ContactHandler(),
        CreatedHandler(),
        DescriptionHandler(),
        GeoHandler(),
        LastModifiedHandler(),
        LocationHandler(),
        OrganizerHandler(),
        PercentCompleteHandler(),
        PriorityHandler(),
        RecurrenceFieldsHandler(),
        RelatedToHandler(),
        ResourcesHandler(),
        SequenceHandler(),
        StatusHandler(),
        SummaryHandler(),
        TimeFieldsHandler(),
        UidHandler(),
        UnknownPropertiesHandler(),
        UrlHandler(),
        ExtendedStatusHandler(),
    )

    /**
     * Maps a jtx Board object with its exceptions to [VJournal] or [VToDo].
     *
     * VJOURNAL and VTODO must have a valid `UID`. So this method generates an UID, if necessary.
     * If an `UID` was generated, it is noted in the result.
     */
    fun mapToCalendarComponents(jtxObjectAndExceptions: JtxObjectAndExceptions): MappingResult {
        // make sure that main jtx object has a UID
        var generatedUid = false
        val mainValues = jtxObjectAndExceptions.main.entityValues
        val uid = mainValues.getAsString(JtxContract.JtxICalObject.UID) ?: run {
            val newUid = UUID.randomUUID().toString()
            mainValues.put(JtxContract.JtxICalObject.UID, newUid)
            generatedUid = true
            newUid
        }

        // map main jtx object
        val main = mapJtxObject(
            entity = jtxObjectAndExceptions.main,
            main = jtxObjectAndExceptions.main
        )

        val rRules = main.getProperties<RRule<*>>(Property.RRULE)
        val exceptions: List<CalendarComponent> = if (rRules.isNotEmpty()) {
            // add exceptions to recurring main jtx object
            jtxObjectAndExceptions.exceptions.map { exception ->
                mapJtxObject(
                    entity = exception,
                    main = jtxObjectAndExceptions.main
                )
            }
        } else {
            emptyList()
        }

        return MappingResult(
            associatedComponents = AssociatedComponents(
                main = main,
                exceptions = exceptions,
                prodId = prodId
            ),
            uid = uid,
            generatedUid = generatedUid
        )
    }

    /**
     * Maps data of a jtx object from the content provider to [VJournal] or [VToDo].
     *
     * @param entity jtx object row as returned by the jtx Board content provider
     * @param main main jtx object row as returned by the jtx Board content provider
     *
     * @return generated data object
     */
    private fun mapJtxObject(entity: Entity, main: Entity): CalendarComponent {
        val entityComponent = entity.getComponent()
        val mainComponent = main.getComponent()

        require(entityComponent == mainComponent) {
            "'main' and 'entity' need to be of same jtx Board component type"
        }

        val calendarComponent = when (entityComponent) {
            JtxContract.JtxICalObject.Component.VJOURNAL -> VJournal()
            JtxContract.JtxICalObject.Component.VTODO -> VToDo()
        }

        for (handler in entityHandlers) {
            handler.process(from = entity, main = main, to = calendarComponent)
        }

        return calendarComponent
    }

    private fun Entity.getComponent(): JtxContract.JtxICalObject.Component {
        val componentValue = entityValues.getAsString(JtxContract.JtxICalObject.COMPONENT)
        return JtxContract.JtxICalObject.Component.valueOf(componentValue)
    }

    /**
     * Result of the [mapToCalendarComponents] operation.
     *
     * @param associatedComponents mapped jtx objects
     * @param uid UID of the mapped jtx objects
     * @param generatedUid whether [uid] was generated by [mapToCalendarComponents]
     *   (*false*: `UID` was already present before mapping)
     */
    class MappingResult(
        val associatedComponents: AssociatedComponents<CalendarComponent>,
        val uid: String,
        val generatedUid: Boolean
    )
}
