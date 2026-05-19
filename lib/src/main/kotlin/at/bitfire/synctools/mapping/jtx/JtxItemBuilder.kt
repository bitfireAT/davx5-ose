/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.jtx

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.AssociatedComponents
import at.bitfire.synctools.mapping.jtx.builder.CollectionIdBuilder
import at.bitfire.synctools.mapping.jtx.builder.DescriptionBuilder
import at.bitfire.synctools.mapping.jtx.builder.JtxEntityBuilder
import at.bitfire.synctools.mapping.jtx.builder.RecurrenceFieldsBuilder
import at.bitfire.synctools.mapping.jtx.builder.RemindersBuilder
import at.bitfire.synctools.mapping.jtx.builder.SyncPropertiesBuilder
import at.bitfire.synctools.mapping.jtx.builder.TimeFieldsBuilder
import at.bitfire.synctools.storage.jtx.JtxItemAndExceptions
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VJournal
import net.fortuna.ical4j.model.component.VToDo

/**
 * Mapper from an [AssociatedComponents] data object to jtx Board content provider data rows.
 */
class JtxItemBuilder(
    collectionId: Long,
    fileName: String?,
    eTag: String?,
    scheduleTag: String?,
    flags: Int
) {

    private val fieldBuilders: Array<JtxEntityBuilder> = arrayOf(
        CollectionIdBuilder(collectionId),
        SyncPropertiesBuilder(fileName, eTag, scheduleTag, flags),

        DescriptionBuilder(),
        RecurrenceFieldsBuilder(),
        TimeFieldsBuilder(),
        RemindersBuilder()
    )

    fun build(component: AssociatedComponents<CalendarComponent>): JtxItemAndExceptions {
        requireJtxComponents(component)

        val main = component.main ?: error("Main item required")
        return JtxItemAndExceptions(
            main = buildComponent(from = main, main = main),
            exceptions = component.exceptions.map { exception ->
                buildComponent(from = exception, main = main)
            }
        )
    }

    private fun buildComponent(from: CalendarComponent, main: CalendarComponent): Entity {
        val entity = Entity(ContentValues())

        for (builder in fieldBuilders) {
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
