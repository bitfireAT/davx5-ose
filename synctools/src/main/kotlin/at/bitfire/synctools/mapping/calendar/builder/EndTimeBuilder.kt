/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.annotation.VisibleForTesting
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.TimeApiExtensions.abs
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.icalendar.requireDtStart
import at.bitfire.synctools.util.AndroidTimeUtils.androidTimezoneId
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import at.bitfire.synctools.util.AndroidTimeUtils.toZonedDateTime
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import java.time.Duration
import java.time.LocalDate
import java.time.Period
import java.time.ZonedDateTime
import java.time.temporal.Temporal
import java.time.temporal.TemporalAmount
import kotlin.jvm.optionals.getOrNull

class EndTimeBuilder: AndroidEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val values = to.entityValues

        /* The calendar provider requires
           - DTEND when the event is non-recurring, and
           - DURATION when the event is recurring.

        So we'll skip if this event is a recurring main event (only main events can be recurring). */
        val rRules = from.getProperties<RRule<*>>(Property.RRULE)
        val rDates = from.getProperties<RDate<*>>(Property.RDATE)
        if (from === main && (rRules.isNotEmpty() || rDates.isNotEmpty())) {
            values.putNull(Events.DTEND)
            return
        }

        val startDate = from.requireDtStart<Temporal>().normalizedDate()

        // potentially calculate DTEND from DTSTART + DURATION, and always align with DTSTART value type
        val calculatedEndDate = from.getEndDate<Temporal>(/* deriveFromDuration = */ false).getOrNull()?.normalizedDate()
            ?.let { alignWithDtStart(endDate = it, startDate = startDate) }
            ?: calculateFromDuration(startDate, from.duration?.duration)

        // ignore DTEND when not after DTSTART and use default duration, if necessary
        val endDate = calculatedEndDate
            ?.takeIf { it.toTimestamp() > startDate.toTimestamp() }  // only use DTEND if it's after DTSTART [1]
            ?: calculateFromDefault(startDate)

        /**
         * [1] RFC 5545 3.8.2.2 Date-Time End:
         * […] its value MUST be later in time than the value of the "DTSTART" property.
         */

        // end time: UNIX timestamp
        values.put(Events.DTEND, endDate.toTimestamp())

        // end time: timezone ID
        values.put(Events.EVENT_END_TIMEZONE, endDate.androidTimezoneId())
    }


    /**
     * Aligns the given DTEND to the VALUE-type (DATE-TIME/DATE) of DTSTART.
     *
     * @param endDate   DTEND date to be aligned
     * @param startDate DTSTART date to compare with
     *
     * @return
     *
     * - DTEND and DTSTART are both either DATE or DATE-TIME → original DTEND
     * - DTEND is DATE, DTSTART is DATE-TIME → DTEND is amended to DATE-TIME with time and timezone from DTSTART
     * - DTEND is DATE-TIME, DTSTART is DATE → DTEND is reduced to its date component
     *
     * @see at.bitfire.synctools.mapping.calendar.handler.RecurrenceFieldsHandler.alignUntil
     */
    @VisibleForTesting
    internal fun alignWithDtStart(endDate: Temporal, startDate: Temporal): Temporal {
        return if (endDate is LocalDate) {
            // DTEND is DATE
            if (DateUtils.isDate(startDate)) {
                // DTEND is DATE, DTSTART is DATE
                endDate
            } else {
                // DTEND is DATE, DTSTART is DATE-TIME → amend with time and timezone
                val startZonedDateTime = startDate.toZonedDateTime()

                ZonedDateTime.of(
                    endDate,
                    startZonedDateTime.toLocalTime(),
                    startZonedDateTime.zone
                )
            }
        } else {
            // DTEND is DATE-TIME
            if (DateUtils.isDate(startDate)) {
                // DTEND is DATE-TIME, DTSTART is DATE → only take date part
                endDate.toZonedDateTime().toLocalDate()
            } else {
                // DTEND is DATE-TIME, DTSTART is DATE-TIME
                endDate
            }
        }
    }

    /**
     * Calculates the DTEND date from DTSTART date + DURATION, if possible.
     *
     * @param startDate start date/date-time
     * @param duration  (optional) duration
     *
     * @return end date/date-time (same value type as [startDate]) or `null` if [duration] was not given
     */
    @VisibleForTesting
    internal fun calculateFromDuration(startDate: Temporal, duration: TemporalAmount?): Temporal? {
        if (duration == null)
            return null

        val dur = duration.abs()   // always take positive temporal amount

        return if (DateUtils.isDate(startDate)) {
            // DTSTART is DATE
            when (dur) {
                is Period -> {
                    // date-based amount of time ("4 days")
                    startDate + dur
                }

                is Duration -> {
                    // time-based amount of time ("34 minutes")
                    val days = dur.toDays()
                    startDate + Period.ofDays(days.toInt())
                }

                else -> {
                    throw IllegalArgumentException("duration argument is neither Period nor Duration")
                }
            }
        } else {
            // DTSTART is DATE-TIME
            // We can add both date-based (Period) and time-based (Duration) amounts of time to an exact date/time.
            startDate.toZonedDateTime() + dur
        }
    }

    /**
     * Chooses a DTEND value for the content provider when the iCalendar doesn't have a DTEND.
     *
     * RFC 5545 says the following about empty DTEND values:
     *
     * > For cases where a "VEVENT" calendar component specifies a "DTSTART" property with a DATE value type but no
     * > "DTEND" nor "DURATION" property, the event's duration is taken to be one day. For cases where a "VEVENT" calendar
     * > component specifies a "DTSTART" property with a DATE-TIME value type but no "DTEND" property, the event
     * > ends on the same calendar date and time of day specified by the "DTSTART" property.
     *
     * In iCalendar, `DTEND` is non-inclusive and must be at a later time than `DTEND`. However, in Android we can use
     * the same value for both the `DTSTART` and the `DTEND` field, and so we use this to indicate a missing DTEND in
     * the original iCalendar.
     *
     * @param startDate   start time to calculate end time from
     * @return End time to use for content provider:
     *
     * - when [startDate] is a `DATE`: [startDate] + 1 day
     * - when [startDate] is a `DATE-TIME`: [startDate]
     */
    @VisibleForTesting
    internal fun calculateFromDefault(startDate: Temporal): Temporal =
        if (startDate is LocalDate) {
            // DATE → one day duration
            startDate.plusDays(1)
        } else {
            // DATE-TIME → same as DTSTART to indicate there was no DTEND set
            startDate
        }

}