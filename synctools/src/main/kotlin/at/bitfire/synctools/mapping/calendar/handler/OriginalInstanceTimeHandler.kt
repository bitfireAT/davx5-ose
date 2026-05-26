/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.DatePropertyTzMapper
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.util.AndroidTimeUtils.isUtcTzId
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.RecurrenceId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class OriginalInstanceTimeHandler: AndroidEventFieldHandler {

    override fun process(from: Entity, main: Entity, to: VEvent) {
        // only applicable to exceptions, not to main events
        if (from === main)
            return

        val values = from.entityValues
        values.getAsLong(Events.ORIGINAL_INSTANCE_TIME)?.let { originalInstanceTime ->
            val originalAllDay = (values.getAsInteger(Events.ORIGINAL_ALL_DAY) ?: 0) != 0
            val instant = Instant.ofEpochMilli(originalInstanceTime)
            to += if (originalAllDay) {
                RecurrenceId(LocalDate.ofInstant(instant, ZoneOffset.UTC))
            } else {
                val mainTzId = main.entityValues.getAsString(Events.EVENT_TIMEZONE)
                if (isUtcTzId(mainTzId)) {
                    RecurrenceId(instant)
                } else {
                    val zoneId = getZoneId(mainTzId)
                    RecurrenceId(ZonedDateTime.ofInstant(instant, zoneId))
                }
            }
        }
    }

    private fun getZoneId(tzId: String?): ZoneId? {
        val timezone = DatePropertyTzMapper.systemTzId(tzId)
        return if (timezone != null) {
            ZoneId.of(timezone)
        } else {
            ZoneId.systemDefault()
        }
    }

}