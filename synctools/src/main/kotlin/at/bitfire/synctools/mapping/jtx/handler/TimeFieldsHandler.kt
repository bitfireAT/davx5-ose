/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.parameter.TzId
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.Duration
import java.time.temporal.Temporal

/**
 * Handles the iCalendar properties: DTSTART, DTEND, DUE, DURATION
 */
class TimeFieldsHandler : JtxObjectEntityHandler {
    override fun process(from: Entity, main: Entity, to: CalendarComponent) {
        // The builder already parses only the required properties based on VJOURNAL or VTODO, and ignores due if needed, so we can just add the ones that are present
        appendField(from, to, JtxContract.JtxICalObject.DTSTART, JtxContract.JtxICalObject.DTSTART_TIMEZONE) { p, v -> DtStart<Temporal>(p, v) }
        appendField(from, to, JtxContract.JtxICalObject.DTEND, JtxContract.JtxICalObject.DTEND_TIMEZONE) { p, v -> DtEnd<Temporal>(p, v) }
        appendField(from, to, JtxContract.JtxICalObject.DUE, JtxContract.JtxICalObject.DUE_TIMEZONE) { p, v -> Due<Temporal>(p, v) }

        from.entityValues.getAsString(JtxContract.JtxICalObject.DURATION)?.let { duration ->
            to += Duration(duration)
        }
    }

    private fun appendField(from: Entity, to: CalendarComponent, property: String, timezoneProperty: String, builder: (ParameterList, String) -> Property) {
        // If the actual property value is missing, ignore the field
        val value = from.entityValues.getAsString(property) ?: return
        val timeZoneName: String? = from.entityValues.getAsString(timezoneProperty)
        if (timeZoneName != null) {
            val tzId = TzId(timeZoneName)
            to += builder(ParameterList(listOf(tzId)), value)
        } else {
            to += builder(ParameterList(), value)
        }
    }
}
