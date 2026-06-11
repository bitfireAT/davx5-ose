/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.Duration
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.Temporal
import java.util.logging.Logger

/**
 * Handles the iCalendar properties: DTSTART, DTEND, DUE, DURATION
 */
class TimeFieldsHandler : JtxObjectEntityHandler {
    private val logger = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: CalendarComponent) {
        appendDateField(from, to, JtxContract.JtxICalObject.DTSTART, JtxContract.JtxICalObject.DTSTART_TIMEZONE) { DtStart(it) }
        appendDateField(from, to, JtxContract.JtxICalObject.DTEND, JtxContract.JtxICalObject.DTEND_TIMEZONE) { DtEnd(it) }
        appendDateField(from, to, JtxContract.JtxICalObject.DUE, JtxContract.JtxICalObject.DUE_TIMEZONE) { Due(it) }

        from.entityValues.getAsString(JtxContract.JtxICalObject.DURATION)?.let { duration ->
            to += Duration(duration)
        }
    }

    private fun appendDateField(from: Entity, to: CalendarComponent, property: String, timezoneProperty: String, builder: (Temporal) -> Property) {
        val epochMillis = from.entityValues.getAsLong(property) ?: return
        val instant = Instant.ofEpochMilli(epochMillis)
        val timezone = from.entityValues.getAsString(timezoneProperty)

        val temporal: Temporal = when {
            timezone == JtxContract.JtxICalObject.TZ_ALLDAY -> LocalDate.ofInstant(instant, ZoneOffset.UTC)
            timezone == ZoneOffset.UTC.id -> instant
            timezone.isNullOrEmpty() -> LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
            else -> try {
                ZonedDateTime.ofInstant(instant, ZoneId.of(timezone))
            } catch (e: DateTimeException) {
                logger.warning("Invalid timezone '$timezone', interpreting as UTC. Error: $e")
                instant
            }
        }

        to += builder(temporal)
    }
}
