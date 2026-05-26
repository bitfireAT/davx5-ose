/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.util.TimeApiExtensions.abs
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.util.AndroidTimeUtils
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtEnd
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Maps a potentially present [Events.DURATION] to a VEvent [DtEnd] property.
 *
 * Does nothing when:
 *
 * - [Events.DTEND] is present / not null (because DTEND then takes precedence over DURATION), and/or
 * - [Events.DURATION] is null / not present.
 */
class DurationHandler: AndroidEventFieldHandler {

    override fun process(from: Entity, main: Entity, to: VEvent) {
        val values = from.entityValues

        /* Skip if DTEND is set and/or DURATION is not set. In both cases EndTimeHandler is
        responsible for generating the DTEND property. */
        if (values.getAsLong(Events.DTEND) != null)
            return
        val durationStr = values.getAsString(Events.DURATION) ?: return

        // parse duration and invert in case of negative value (events can't go back in time)
        val parsedDuration = AndroidTimeUtils.parseDuration(durationStr)
        val duration = parsedDuration.abs()

        /* Some servers have problems with DURATION. For maximum compatibility, we always generate DTEND instead of DURATION.
        (After all, the constraint that non-recurring events have a DTEND while recurring events use DURATION is Android-specific.)
        So we have to calculate DTEND from DTSTART and its timezone plus DURATION. */

        val tsStart = values.getAsLong(Events.DTSTART) ?: return
        val allDay = (values.getAsInteger(Events.ALL_DAY) ?: 0) != 0

        if (allDay) {
            val startTimeUTC = Instant.ofEpochMilli(tsStart).atOffset(ZoneOffset.UTC)
            val endDate = (startTimeUTC + duration).toLocalDate()

            // DATE
            to += DtEnd(endDate)

        } else {
            // DATE-TIME
            val startDateTime = AndroidTimeField(
                timestamp = tsStart,
                timeZone = values.getAsString(Events.EVENT_TIMEZONE),
                allDay = false
            ).toTemporal()

            val end = when (startDateTime) {
                is Instant -> startDateTime + duration
                is ZonedDateTime -> startDateTime + duration
                else -> {
                    error("Unsupported Temporal type: ${startDateTime::class.qualifiedName}")
                }
            }

            to += DtEnd(end)
        }
    }

}