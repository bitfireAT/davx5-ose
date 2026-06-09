/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.Entity
import android.provider.CalendarContract.ExtendedProperties
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.storage.calendar.EventsContract
import net.fortuna.ical4j.model.TextList
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Categories

class CategoriesHandler : AndroidEventEntityHandler {

    override fun process(from: Entity, main: Entity, to: VEvent) {
        val extended = from.subValues.filter { it.uri == ExtendedProperties.CONTENT_URI }.map { it.values }
        val categories = extended.firstOrNull { it.getAsString(ExtendedProperties.NAME) == EventsContract.EXTNAME_CATEGORIES }
        val listValue = categories?.getAsString(ExtendedProperties.VALUE)
        if (listValue != null) {
            to += Categories(TextList(
                listValue.split(EventsContract.CATEGORIES_SEPARATOR)
            ))
        }
    }

}