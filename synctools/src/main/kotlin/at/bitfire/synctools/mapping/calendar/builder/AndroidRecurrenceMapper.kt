/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.synctools.util.AndroidTimeUtils.toInstant
import at.bitfire.synctools.util.AndroidTimeUtils.toZonedDateTime
import net.fortuna.ical4j.model.Property
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.Temporal
import java.util.Locale

object AndroidRecurrenceMapper {

    private const val RECURRENCE_LIST_TZID_SEPARATOR = ';'
    private const val RECURRENCE_LIST_VALUE_SEPARATOR = ","

    /**
     * Used to separate multiple RRULEs/EXRULEs in the RRULE/EXRULE storage field.
     */
    private const val RECURRENCE_RULE_SEPARATOR = "\n"


    /**
     * Format multiple RRULEs/EXRULEs as string to be used with Android's calendar provider.
     */
    fun androidRecurrenceRuleString(rules: List<Property>): String {
        return rules.joinToString(RECURRENCE_RULE_SEPARATOR) { it.value }
    }

    /**
     * Concatenates, if necessary, multiple RDATE/EXDATE lists and converts them to
     * a formatted string which Android calendar provider can process.
     *
     * Android [expects this format](https://android.googlesource.com/platform/frameworks/opt/calendar/+/68b3632330e7a9a4f9813b7eb671dbfd78c25bcd/src/com/android/calendarcommon2/RecurrenceSet.java#138):
     * `[TZID;]date1,date2,date3` where date is `yyyymmddThhmmss` (when
     * TZID is given) or `yyyymmddThhmmssZ`.
     *
     * This method converts the values to the type of [startDate], if necessary:
     *
     * - DTSTART (DATE-TIME) and RDATE/EXDATE (DATE) → method converts RDATE/EXDATE to DATE-TIME with same time as DTSTART
     * - DTSTART (DATE) and RDATE/EXDATE (DATE-TIME) → method converts RDATE/EXDATE to DATE (just drops time)
     *
     * @param dates     list of `Temporal`s from RDATE or EXDATE properties
     * @param startDate used to determine whether the event is an all-day event or not; also used to
     *                  generate the date-time if the event is not all-day but the exception is
     *
     * @return formatted string for Android calendar provider
     */
    fun androidRecurrenceDatesString(dates: List<Temporal>, startDate: Temporal): String {
        /*  rdate/exdate:       DATE                                DATE_TIME
            all-day             store as ...T000000Z                cut off time and store as ...T000000Z
            event with time     use time and zone from DTSTART      store as ...ThhmmssZ
        */
        val utcDateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
        val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.ROOT)
        val allDay = DateUtils.isDate(startDate)

        // use time zone of first entry for the whole set; null for UTC
        val zoneId = (dates.firstOrNull() as? ZonedDateTime)?.zone
        val targetZone: ZoneId = zoneId ?: ZoneOffset.UTC

        val convertedDates = dates.map { date ->
            when {
                allDay -> {
                    // DTSTART is DATE; store as <date>T000000Z for Android
                    if (date is LocalDate) {
                        // RDATE/EXDATE is DATE
                        date.atStartOfDay(ZoneOffset.UTC)
                    } else {
                        // RDATE/EXDATE is DATE-TIME, drop time part
                        date.toLocalDate().atStartOfDay(ZoneOffset.UTC)
                    }
                }
                // from now on, we know that DTSTART is DATE-TIME

                date is LocalDate -> {
                    // DTSTART is DATE-TIME, RDATE/EXDATE is DATE; amend with clock time from DTSTART
                    val zonedStartDate = startDate.toZonedDateTime()
                    ZonedDateTime.of(
                        /* date = */ date,
                        /* time = */ zonedStartDate.toLocalTime(),
                        /* zone = */ zonedStartDate.zone
                    ).withZoneSameInstant(targetZone)
                }
                else ->
                    // Both DATE-TIME
                    date.toInstant().atZone(targetZone)
            }
        }

        // format expected by Android: [tzid;]value1,value2,...
        return buildString {
            if (zoneId != null) {
                append(zoneId.id)
                append(RECURRENCE_LIST_TZID_SEPARATOR)
            }

            val formatter = if (zoneId == null) utcDateFormatter else dateFormatter
            convertedDates.joinTo(buffer = this, separator = RECURRENCE_LIST_VALUE_SEPARATOR) {
                formatter.format(it)
            }
        }
    }

}