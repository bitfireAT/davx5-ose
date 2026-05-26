/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.icalendar.recurrenceId
import at.bitfire.synctools.icalendar.requireDtStart
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import at.bitfire.synctools.util.AndroidTimeUtils.toZonedDateTime
import net.fortuna.ical4j.model.component.VEvent
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.temporal.Temporal

class OriginalInstanceTimeBuilder: AndroidEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val values = to.entityValues
        if (from !== main) {
            // only for exceptions
            val originalDtStart = main.requireDtStart<Temporal>()
            values.put(Events.ORIGINAL_ALL_DAY, if (DateUtils.isDate(originalDtStart)) 1 else 0)

            var recurrenceDate = from.recurrenceId?.normalizedDate()
            val originalDate = originalDtStart.normalizedDate()

            // rewrite recurrenceDate, if necessary
            if (DateUtils.isDateTime(recurrenceDate) && DateUtils.isDate(originalDate)) {
                // rewrite RECURRENCE-ID;VALUE=DATE-TIME to VALUE=DATE for all-day events
                recurrenceDate = recurrenceDate!!.toZonedDateTime().toLocalDate()

            } else if (recurrenceDate is LocalDate && DateUtils.isDateTime(originalDate)) {
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