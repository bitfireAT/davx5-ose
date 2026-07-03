/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.util

import at.bitfire.synctools.util.AndroidTimeUtils.isUtcTzId
import at.bitfire.synctools.util.AndroidTimeUtils.toZonedDateTime
import at.bitfire.synctools.util.TimeApiExtensions.isDateTime
import at.bitfire.synctools.util.TimeApiExtensions.toLocalDate
import net.fortuna.ical4j.model.Recur
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.Temporal

object RecurrenceUtils {

    /**
     * Aligns the `UNTIL` of the given recurrence info to the VALUE-type (DATE-TIME/DATE) of [startTemporal].
     *
     * If the aligned `UNTIL` is a DATE-TIME, this method also makes sure that it's specified in UTC format
     * as required by RFC 5545 3.3.10.
     *
     * @return
     *
     * - UNTIL not set -> original recur
     * - UNTIL and DTSTART are both either DATE or DATE-TIME -> original recur
     * - UNTIL is DATE, DTSTART is DATE-TIME -> UNTIL is amended to DATE-TIME with time and timezone from DTSTART
     * - UNTIL is DATE-TIME, DTSTART is DATE -> UNTIL is reduced to its date component
     *
     * @see at.bitfire.synctools.mapping.calendar.builder.EndTimeBuilder.alignWithDtStart
     */
    fun alignUntil(recur: Recur<Temporal>, startTemporal: Temporal): Recur<Temporal> {
        val until: Temporal = recur.until ?: return recur

        return when {
            until.isDateTime() && startTemporal.isDateTime() -> {
                val untilZoned = until.toZonedDateTime()
                if (isUtcTzId(untilZoned.zone.id))
                    recur
                else
                    Recur.Builder(recur)
                        .until(untilZoned.withZoneSameInstant(ZoneOffset.UTC).toInstant())
                        .build()
            }

            until.isDateTime() ->
                Recur.Builder(recur)
                    .until(until.toLocalDate())
                    .build()

            startTemporal.isDateTime() -> {
                val untilDate = until.toLocalDate()
                val startTime = startTemporal.toZonedDateTime()
                val untilDateWithTime = ZonedDateTime.of(untilDate, startTime.toLocalTime(), startTime.zone)

                Recur.Builder(recur)
                    .until(untilDateWithTime.toInstant())
                    .build()
            }

            else ->
                recur
        }
    }

}
