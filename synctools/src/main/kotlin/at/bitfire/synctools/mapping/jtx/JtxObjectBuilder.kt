/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.AssociatedComponents
import at.bitfire.synctools.mapping.jtx.builder.AttachmentsBuilder
import at.bitfire.synctools.mapping.jtx.builder.AttendeesBuilder
import at.bitfire.synctools.mapping.jtx.builder.CategoriesBuilder
import at.bitfire.synctools.mapping.jtx.builder.ClassificationBuilder
import at.bitfire.synctools.mapping.jtx.builder.CollectionIdBuilder
import at.bitfire.synctools.mapping.jtx.builder.ColorBuilder
import at.bitfire.synctools.mapping.jtx.builder.CommentsBuilder
import at.bitfire.synctools.mapping.jtx.builder.CompletedBuilder
import at.bitfire.synctools.mapping.jtx.builder.ComponentBuilder
import at.bitfire.synctools.mapping.jtx.builder.ContactBuilder
import at.bitfire.synctools.mapping.jtx.builder.CreatedBuilder
import at.bitfire.synctools.mapping.jtx.builder.DescriptionBuilder
import at.bitfire.synctools.mapping.jtx.builder.DirtyAndDeletedBuilder
import at.bitfire.synctools.mapping.jtx.builder.DtStampBuilder
import at.bitfire.synctools.mapping.jtx.builder.ExtendedStatusBuilder
import at.bitfire.synctools.mapping.jtx.builder.GeoBuilder
import at.bitfire.synctools.mapping.jtx.builder.JtxObjectBinaryDataRowBuilder
import at.bitfire.synctools.mapping.jtx.builder.JtxObjectEntityBuilder
import at.bitfire.synctools.mapping.jtx.builder.LastModifiedBuilder
import at.bitfire.synctools.mapping.jtx.builder.LocationBuilder
import at.bitfire.synctools.mapping.jtx.builder.OrganizerBuilder
import at.bitfire.synctools.mapping.jtx.builder.PercentCompleteBuilder
import at.bitfire.synctools.mapping.jtx.builder.PriorityBuilder
import at.bitfire.synctools.mapping.jtx.builder.RecurrenceFieldsBuilder
import at.bitfire.synctools.mapping.jtx.builder.RelatedToBuilder
import at.bitfire.synctools.mapping.jtx.builder.RemindersBuilder
import at.bitfire.synctools.mapping.jtx.builder.ResourcesBuilder
import at.bitfire.synctools.mapping.jtx.builder.SequenceBuilder
import at.bitfire.synctools.mapping.jtx.builder.StatusBuilder
import at.bitfire.synctools.mapping.jtx.builder.SummaryBuilder
import at.bitfire.synctools.mapping.jtx.builder.SyncPropertiesBuilder
import at.bitfire.synctools.mapping.jtx.builder.TimeFieldsBuilder
import at.bitfire.synctools.mapping.jtx.builder.UidBuilder
import at.bitfire.synctools.mapping.jtx.builder.UnknownPropertiesBuilder
import at.bitfire.synctools.mapping.jtx.builder.UrlBuilder
import at.bitfire.synctools.storage.jtx.JtxEntity
import at.bitfire.synctools.storage.jtx.JtxEntityAndExceptions
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo

/**
 * Mapper from an [AssociatedComponents] data object to jtx Board content provider data rows.
 */
class JtxObjectBuilder(
    collectionId: Long,
    fileName: String?,
    eTag: String?,
    scheduleTag: String?,
    flags: Int
) {

    /* Note: the storage layer (JtxCollection) doesn't read/write all sub-rows,
    but only those defined in JtxCollection.SUB_VALUE_URIS – so all sub-rows
    that are supported by builders/handlers should also be present there. */

    private val entityBuilders: Array<JtxObjectEntityBuilder> = arrayOf(
        // Metadata
        CollectionIdBuilder(collectionId),
        ComponentBuilder(),
        SyncPropertiesBuilder(fileName, eTag, scheduleTag, flags),
        DirtyAndDeletedBuilder(),

        // Main fields
        UidBuilder(),
        SummaryBuilder(),
        DescriptionBuilder(),
        ClassificationBuilder(),
        PriorityBuilder(),
        StatusBuilder(),
        ExtendedStatusBuilder(),
        PercentCompleteBuilder(),
        CompletedBuilder(),
        CreatedBuilder(),
        LastModifiedBuilder(),
        DtStampBuilder(),
        SequenceBuilder(),

        // Extra info
        ColorBuilder(),
        ContactBuilder(),
        GeoBuilder(),
        LocationBuilder(),
        UrlBuilder(),

        // Time fields, recurrence
        TimeFieldsBuilder(),
        RecurrenceFieldsBuilder(),

        // Sub-rows
        AttendeesBuilder(),
        CategoriesBuilder(),
        CommentsBuilder(),
        OrganizerBuilder(),
        RelatedToBuilder(),
        RemindersBuilder(),
        ResourcesBuilder(),
        UnknownPropertiesBuilder(),
    )

    private val binaryDataRowBuilders = arrayOf<JtxObjectBinaryDataRowBuilder>(
        AttachmentsBuilder(),
    )

    fun build(component: AssociatedComponents<CalendarComponent>): JtxEntityAndExceptions {
        requireJtxComponents(component)

        val main = component.main ?: createMainFromExceptions(component.exceptions)
        return JtxEntityAndExceptions(
            main = buildComponent(from = main, main = main),
            exceptions = component.exceptions.map { exception ->
                buildComponent(from = exception, main = main)
            }
        )
    }

    private fun buildComponent(from: CalendarComponent, main: CalendarComponent): JtxEntity {
        val entity = Entity(ContentValues())

        for (builder in entityBuilders) {
            builder.build(from = from, main = main, to = entity)
        }

        val dataSubValues = buildList {
            for (builder in binaryDataRowBuilders) {
                val dataSubValues = builder.build(from)
                addAll(dataSubValues)
            }
        }

        return JtxEntity(entity, dataSubValues)
    }

    private fun createMainFromExceptions(exceptions: List<CalendarComponent>): CalendarComponent {
        // Should in the future be replaced by a real component that has a title like "(unknown task)".
        // This main object should also have a special extended property that indicates that the component
        // must not actually be generated as main VToDo/VJournal when the object is locally edited and then uploaded.
        return exceptions.firstOrNull()
            ?: throw IllegalArgumentException("Either main component or at least one exception required")
    }

    private fun requireJtxComponents(component: AssociatedComponents<CalendarComponent>) {
        when (component.main) {
            null -> {
                // If we're only invited to an exception, we don't have a main component.
            }
            is VJournal -> {
                require(component.exceptions.all { it is VJournal }) {
                    "Exceptions need to be of same type as main component"
                }
            }
            is VToDo -> {
                require(component.exceptions.all { it is VToDo }) {
                    "Exceptions need to be of same type as main component"
                }
            }
            else -> {
                throw IllegalArgumentException("Only VJournal and VToDo are supported")
            }
        }
    }
}
