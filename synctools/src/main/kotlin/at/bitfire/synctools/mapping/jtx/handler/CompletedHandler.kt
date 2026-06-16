/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.Entity
import at.bitfire.ical4android.JtxICalObject
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.Completed
import net.fortuna.ical4j.model.property.XProperty
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class CompletedHandler : JtxObjectEntityHandler {
    override fun process(from: Entity, main: Entity, to: CalendarComponent) {
        if (to !is VToDo) return

        val completed = from.entityValues.getAsLong(JtxContract.JtxICalObject.COMPLETED) ?: return
        val instant = Instant.ofEpochMilli(completed)
        val timezone = from.entityValues.getAsString(JtxContract.JtxICalObject.COMPLETED_TIMEZONE)

        if (timezone == JtxContract.JtxICalObject.TZ_ALLDAY) {
            // All-day completion: no time of day, COMPLETED is stored as midnight UTC
            val date = LocalDate.ofInstant(instant, ZoneOffset.UTC)
            to += Completed(ParameterList(listOf(Value.DATE)), DateTimeFormatter.BASIC_ISO_DATE.format(date))
            to += XProperty(JtxICalObject.X_PROP_COMPLETEDTIMEZONE, timezone)
            return
        }

        // Completed is always emitted in UTC; the timezone is only kept as metadata
        to += Completed(instant)

        if (!timezone.isNullOrBlank() && isValidTimeZone(timezone)) {
            to += XProperty(JtxICalObject.X_PROP_COMPLETEDTIMEZONE, timezone)
        }
        // invalid/blank timezone values are dropped (the date stays valid as UTC)
    }

    private fun isValidTimeZone(id: String): Boolean =
        try {
            ZoneId.of(id)
            true
        } catch (_: DateTimeException) {
            false
        }
}
