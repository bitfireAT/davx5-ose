/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import at.bitfire.synctools.util.AndroidTimeUtils
import at.bitfire.synctools.util.AndroidTimeUtils.isUtcTzId
import net.fortuna.ical4j.util.TimeZones
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.Temporal
import java.time.zone.ZoneRulesException

/**
 * Converts timestamps from the [android.provider.CalendarContract.Events.DTSTART] or [android.provider.CalendarContract.Events.DTEND]
 * fields into other representations.
 *
 * @param timestamp     value of the Android `DTSTART`/`DTEND` field (timestamp in milliseconds)
 * @param timeZone      value of the respective Android timezone field or `null` for system default time zone
 * @param allDay        whether Android `ALL_DAY` field is non-null and not zero
 */
class AndroidTimeField(
    private val timestamp: Long,
    private val timeZone: String?,
    private val allDay: Boolean,
) {

    /** ID of system default timezone */
    private val defaultTzId by lazy { ZoneId.systemDefault().id }

    /**
     * Converts the given Android date/time into java time temporal object.
     *
     * @return `LocalDate` in case of an all-day event, `Instant` in case of a non-all-day event using UTC as time zone,
     * `ZonedDateTime` otherwise.
     */
    fun toTemporal(): Temporal {
        val instant = Instant.ofEpochMilli(timestamp)

        if (allDay)
            return LocalDate.ofInstant(instant, ZoneId.of(timeZone ?: defaultTzId))

        // non-all-day
        val tzId = timeZone
            ?: ZoneId.systemDefault().id    // safe fallback (should never be used/needed because the calendar provider requires EVENT_TIMEZONE)

        if (isUtcTzId(tzId)) {
            return instant
        }

        val timezone = try {
            ZoneId.of(tzId)
        } catch (_: DateTimeException) {
            ZoneId.of(defaultTzId)
        } catch (_: ZoneRulesException) {
            ZoneId.of(defaultTzId)
        }

        return ZonedDateTime.ofInstant(instant, timezone)
    }

}