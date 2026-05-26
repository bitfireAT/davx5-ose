/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.annotation.VisibleForTesting
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtEnd
import java.time.Duration
import java.time.Instant

/**
 * Maps a potentially present [Events.DTEND] to a VEvent [DtEnd] property.
 *
 * If [Events.DTEND] is null / not present:
 *
 * - If [Events.DURATION] is present / not null, [DurationHandler] is responsible for generating the VEvent's [DtEnd].
 * - If [Events.DURATION] is null / not present, this class is responsible for generating the VEvent's [DtEnd].
 */
class EndTimeHandler: AndroidEventFieldHandler {

    override fun process(from: Entity, main: Entity, to: VEvent) {
        val values = from.entityValues
        val allDay = (values.getAsInteger(Events.ALL_DAY) ?: 0) != 0

        // Skip if DTSTART is not present (not allowed in iCalendar)
        val tsStart = values.getAsLong(Events.DTSTART) ?: return

        val tsEndOrNull = values.getAsLong(Events.DTEND)
        val durationStr = values.getAsString(Events.DURATION)

        if (tsEndOrNull == null && durationStr != null) // DTEND not present, but DURATION is present:
            return                                      // DurationHandler is responsible for generating the DTEND property

        /* Make sure that there's always a DTEND for compatibility. While it's allowed in RFC 5545
        to omit DTEND, this causes problems with some servers (notably iCloud). See also:
        https://github.com/bitfireAT/davx5-ose/issues/1859 */
        val tsEnd = tsEndOrNull
            ?.takeUnless { it < tsStart }               // only use DTEND if it's not before DTSTART
            ?: calculateFromDefault(tsStart, allDay)    // always provide DTEND for compatibility

        // DATE or DATE-TIME according to allDay
        val end = AndroidTimeField(
            timestamp = tsEnd,
            timeZone = values.getAsString(Events.EVENT_END_TIMEZONE)
                ?: values.getAsString(Events.EVENT_TIMEZONE),   // if end timezone is not present, assume same as for start
            allDay = allDay
        ).toTemporal()

        to += DtEnd(end)
    }

    @VisibleForTesting
    internal fun calculateFromDefault(tsStart: Long, allDay: Boolean): Long =
        if (allDay) {
            // all-day: default duration is PT1D; all-day events are always in UTC time zone
            val start = Instant.ofEpochMilli(tsStart)
            val end = start + Duration.ofDays(1)
            end.toEpochMilli()
        } else {
            // non-all-day: default duration is PT0S; end time = start time
            tsStart
        }

}