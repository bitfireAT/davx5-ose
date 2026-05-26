/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import at.bitfire.dateTimeValue
import at.bitfire.dateValue
import at.bitfire.synctools.mapping.calendar.builder.AndroidRecurrenceMapper.androidRecurrenceDatesString
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidRecurrenceMapperTest {

    val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()!!
    val tzBerlin: TimeZone = tzRegistry.getTimeZone("Europe/Berlin")!!
    val tzToronto: TimeZone = tzRegistry.getTimeZone("America/Toronto")!!

    @Test
    fun testAndroidRecurrenceDatesString_Date() {
        // DATEs (without time) have to be converted to <date>THHmmssZ for Android
        val dates = listOf(dateValue("20150101"), dateValue("20150702"))
        val startDate = dateValue("20150101")

        val result = androidRecurrenceDatesString(dates, startDate)

        assertEquals("20150101T000000Z,20150702T000000Z", result)
    }

    @Test
    fun testAndroidRecurrenceDatesString_Date_AlthoughDtStartIsDateTime() {
        // DATEs (without time) have to be converted to <date>THHmmssZ for Android
        val dates = listOf(dateValue("20150101"), dateValue("20150702"))
        val startDate = dateTimeValue("20150101T043210", tzBerlin)

        val result = androidRecurrenceDatesString(dates, startDate)

        assertEquals("20150101T033210Z,20150702T023210Z", result)
    }

    @Test
    fun testAndroidRecurrenceDatesString_Date_AlthoughDtStartIsDateTime_MonthWithLessDays() {
        // DATEs (without time) have to be converted to <date>THHmmssZ for Android
        val dates = listOf(dateValue("20240531"))
        val startDate = dateTimeValue("20240401T114500", tzBerlin)

        val result = androidRecurrenceDatesString(dates, startDate)

        assertEquals("20240531T094500Z", result)
    }

    @Test
    fun testAndroidRecurrenceDatesString_Time_AlthoughDtStartIsAllDay() {
        // DATE-TIME (floating time or UTC) recurrences for all-day events have to be converted to <date>T000000Z for Android
        val dates = listOf(dateTimeValue("20150101T000000"), dateTimeValue("20150702T000000Z"))
        val startDate = dateValue("20150101")

        val result = androidRecurrenceDatesString(dates, startDate)

        assertEquals("20150101T000000Z,20150702T000000Z", result)
    }

    @Test
    fun testAndroidRecurrenceDatesString_TwoTimesWithSameTimezone() {
        // two separate entries, both with timezone Toronto
        val dates = listOf(
            dateTimeValue("20150103T113030", tzToronto),
            dateTimeValue("20150704T113040", tzToronto),
        )
        val startDate = dateTimeValue("20150103T113030", tzToronto)

        val result = androidRecurrenceDatesString(dates, startDate)

        assertEquals("America/Toronto;20150103T113030,20150704T113040", result)
    }

    @Test
    fun testAndroidRecurrenceDatesString_TwoTimesWithDifferentTimezone() {
        // two separate entries, one with timezone Toronto, one with Berlin
        // 2015/01/03 11:30:30 Toronto [-5] = 2015/01/03 16:30:30 UTC
        // DST: 2015/07/04 11:30:40 Berlin  [+2] = 2015/07/04 09:30:40 UTC = 2015/07/04 05:30:40 Toronto [-4]
        val dates = listOf(
            dateTimeValue("20150103T113030", tzToronto),
            dateTimeValue("20150704T113040", tzBerlin),
        )
        val startDate = dateTimeValue("20150103T113030", tzToronto)

        val result = androidRecurrenceDatesString(dates, startDate)

        assertEquals("America/Toronto;20150103T113030,20150704T053040", result)
    }

    @Test
    fun testAndroidRecurrenceDatesString_TwoTimesWithOneUtc() {
        // two separate entries, one with timezone Toronto, one with Berlin
        // 2015/01/03 11:30:30 Toronto [-5] = 2015/01/03 16:30:30 UTC
        // DST: 2015/07/04 11:30:40 Berlin  [+2] = 2015/07/04 09:30:40 UTC = 2015/07/04 05:30:40 Toronto [-4]
        val dates = listOf(
            dateTimeValue("20150103T113030Z"),
            dateTimeValue("20150704T113040", tzBerlin),
        )
        val startDate = dateTimeValue("20150103T113030Z")

        val result = androidRecurrenceDatesString(dates, startDate)

        assertEquals("20150103T113030Z,20150704T093040Z", result)
    }

    @Test
    fun testAndroidRecurrenceDatesString_UtcTime() {
        val dates = listOf(dateTimeValue("20150101T103010Z"), dateTimeValue("20150102T103020Z"))
        val startDate = dateTimeValue("20150101T103010Z")

        val result = androidRecurrenceDatesString(dates, startDate)

        assertEquals("20150101T103010Z,20150102T103020Z", result)
    }

}