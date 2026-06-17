/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.Entity
import at.bitfire.synctools.exception.InvalidLocalResourceException
import at.bitfire.synctools.icalendar.due
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VJournal
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
        appendDateField(from, to, JtxContract.JtxICalObject.DTSTART, JtxContract.JtxICalObject.DTSTART_TIMEZONE) { DtStart(it) }

        // DTEND, DUE and DURATION should not be added to VJOURNAL
        if (to is VJournal) return

        appendDateField(from, to, JtxContract.JtxICalObject.DTEND, JtxContract.JtxICalObject.DTEND_TIMEZONE) { DtEnd(it) }
        appendDateField(from, to, JtxContract.JtxICalObject.DUE, JtxContract.JtxICalObject.DUE_TIMEZONE) { Due(it) }

        if (to.due<Temporal>() == null) {
            // Add DURATION if DUE is not set
            from.entityValues.getAsString(JtxContract.JtxICalObject.DURATION)?.let { duration ->
                val missingDtStart = !to.getProperty<DtStart<Temporal>>(DtStart.DTSTART).isPresent
                if (missingDtStart) {
                    throw InvalidLocalResourceException("DURATION is set but DTSTART is missing")
                }

                to += Duration(duration)
            }
        }
    }

    private fun appendDateField(from: Entity, to: CalendarComponent, property: String, timezoneProperty: String, builder: (Temporal) -> Property) {
        val epochMillis = from.entityValues.getAsLong(property) ?: return
        val temporal = JtxTimeField(
            timestamp = epochMillis,
            timeZone = from.entityValues.getAsString(timezoneProperty)
        ).toTemporal()

        to += builder(temporal)
    }
}
