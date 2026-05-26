/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import at.bitfire.DefaultTimezoneRule
import at.bitfire.dateTimeValue
import at.bitfire.dateValue
import at.bitfire.synctools.util.AndroidTimeUtils
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.util.TimeZones
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class AndroidTimeFieldTest {

    @get:Rule
    val tzRule = DefaultTimezoneRule("Pacific/Honolulu")

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()

    @Test
    fun `toTemporal with all-day returns LocalDate`() {
        val androidTimeField = AndroidTimeField(
            timestamp = 1760521619000,      // Wed Oct 15 2025 11:46:59 GMT+0200
            timeZone = "Europe/Paris",
            allDay = true,
        )

        val result = androidTimeField.toTemporal()

        assertEquals(dateValue("20251015"), result)
    }

    @Test
    fun `toTemporal without all-day returns ZonedDateTime`() {
        val androidTimeField = AndroidTimeField(
            timestamp = 1760521619000,      // Wed Oct 15 2025 09:46:59 GMT+0000
            timeZone = "Europe/Vienna",
            allDay = false,
        )

        val result = androidTimeField.toTemporal()

        assertEquals(
            dateTimeValue("20251015T114659", tzRegistry.getTimeZone("Europe/Vienna")),
            result
        )
    }

    @Test
    fun `toTemporal with Android UTC timezone ID returns Instant`() {
        val androidTimeField = AndroidTimeField(
            timestamp = 1760521619000,
            timeZone = AndroidTimeUtils.TZID_UTC,
            allDay = false,
        )

        val result = androidTimeField.toTemporal()

        assertEquals(Instant.ofEpochMilli(1760521619000), result)
    }

    @Test
    fun `toTemporal with JVM UTC timezone ID returns Instant`() {
        val androidTimeField = AndroidTimeField(
            timestamp = 1760521619000,
            timeZone = TimeZones.UTC_ID,
            allDay = false,
        )

        val result = androidTimeField.toTemporal()

        assertEquals(Instant.ofEpochMilli(1760521619000), result)
    }

    @Test
    fun `toTemporal without timezone returns ZonedDateTime with default zone`() {
        val androidTimeField = AndroidTimeField(
            timestamp = 1760521619000,      // Wed Oct 15 2025 09:46:59 GMT+0000
            timeZone = null,
            allDay = false,
        )

        val result = androidTimeField.toTemporal()

        assertEquals(dateTimeValue("20251014T234659", tzRule.defaultZoneId), result)
    }

    @Test
    fun `toTemporal with unknown timezone returns ZonedDateTime with default zone`() {
        val androidTimeField = AndroidTimeField(
            timestamp = 1760521619000,      // Wed Oct 15 2025 09:46:59 GMT+0000
            timeZone = "absolutely/unknown",
            allDay = false,
        )

        val result = androidTimeField.toTemporal()

        assertEquals(dateTimeValue("20251014T234659", tzRule.defaultZoneId), result)
    }

}
