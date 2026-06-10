/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.AssociatedComponents
import at.bitfire.synctools.mapping.jtx.builder.CategoriesBuilder
import at.bitfire.synctools.mapping.jtx.builder.CollectionIdBuilder
import at.bitfire.synctools.mapping.jtx.builder.CommentsBuilder
import at.bitfire.synctools.mapping.jtx.builder.ComponentBuilder
import at.bitfire.synctools.mapping.jtx.builder.DescriptionBuilder
import at.bitfire.synctools.mapping.jtx.builder.ExtendedStatusBuilder
import at.bitfire.synctools.mapping.jtx.builder.JtxObjectEntityBuilder
import at.bitfire.synctools.mapping.jtx.builder.PriorityBuilder
import at.bitfire.synctools.mapping.jtx.builder.RecurrenceFieldsBuilder
import at.bitfire.synctools.mapping.jtx.builder.RemindersBuilder
import at.bitfire.synctools.mapping.jtx.builder.ResourcesBuilder
import at.bitfire.synctools.mapping.jtx.builder.SyncPropertiesBuilder
import at.bitfire.synctools.mapping.jtx.builder.TimeFieldsBuilder
import at.bitfire.synctools.mapping.jtx.builder.UidBuilder
import at.bitfire.synctools.storage.jtx.JtxObjectAndExceptions
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

    private val entityBuilders: Array<JtxObjectEntityBuilder> = arrayOf(
        CollectionIdBuilder(collectionId),
        ComponentBuilder(),
        SyncPropertiesBuilder(fileName, eTag, scheduleTag, flags),

        DescriptionBuilder(),
        PriorityBuilder(),
        ExtendedStatusBuilder(),
        RecurrenceFieldsBuilder(),
        TimeFieldsBuilder(),
        RemindersBuilder(),
        CategoriesBuilder(),
        CommentsBuilder(),
        ResourcesBuilder(),
        UidBuilder(),
    )

    fun build(component: AssociatedComponents<CalendarComponent>): JtxObjectAndExceptions {
        requireJtxComponents(component)

        val main = component.main ?: error("Main component required")
        return JtxObjectAndExceptions(
            main = buildComponent(from = main, main = main),
            exceptions = component.exceptions.map { exception ->
                buildComponent(from = exception, main = main)
            }
        )
    }

    private fun buildComponent(from: CalendarComponent, main: CalendarComponent): Entity {
        val entity = Entity(ContentValues())

        for (builder in entityBuilders) {
            builder.build(from = from, main = main, to = entity)
        }

        return entity
    }

    private fun requireJtxComponents(component: AssociatedComponents<CalendarComponent>) {
        when (component.main) {
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
