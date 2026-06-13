/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.icalendar.isAllDay
import at.bitfire.synctools.icalendar.recurrenceId
import at.bitfire.synctools.icalendar.requireDtStart
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import at.bitfire.synctools.util.AndroidTimeUtils.toZonedDateTime
import at.bitfire.synctools.util.TimeApiExtensions.isDate
import at.bitfire.synctools.util.TimeApiExtensions.isDateTime
import net.fortuna.ical4j.model.component.VEvent
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.Temporal

class OriginalInstanceTimeBuilder : AndroidEventEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val values = to.entityValues
        if (from !== main) {
            // only for exceptions
            val originalDtStart = main.requireDtStart<Temporal>()
            values.put(Events.ORIGINAL_ALL_DAY, if (originalDtStart.isAllDay()) 1 else 0)

            var recurrenceDate = from.recurrenceId?.normalizedDate()
            val originalDate = originalDtStart.normalizedDate()

            // rewrite recurrenceDate, if necessary
            if (recurrenceDate.isDateTime() && originalDate.isDate()) {
                // rewrite RECURRENCE-ID;VALUE=DATE-TIME to VALUE=DATE for all-day events
                recurrenceDate = recurrenceDate!!.toZonedDateTime().toLocalDate()

            } else if (recurrenceDate is LocalDate && originalDate.isDateTime()) {
                // rewrite RECURRENCE-ID;VALUE=DATE to VALUE=DATE-TIME for non-all-day-events
                // guess time and time zone from DTSTART
                val zonedDateTime = originalDate.toZonedDateTime()
                recurrenceDate = ZonedDateTime.of(
                    recurrenceDate,
                    zonedDateTime.toLocalTime(),
                    zonedDateTime.zone
                )
            }
            values.put(Events.ORIGINAL_INSTANCE_TIME, recurrenceDate?.toTimestamp())

        } else {
            // main event
            values.putNull(Events.ORIGINAL_ALL_DAY)
            values.putNull(Events.ORIGINAL_INSTANCE_TIME)
        }
    }

}