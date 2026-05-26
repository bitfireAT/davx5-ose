/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.util.TimeZones
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class VTimeZoneMinifierTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    val tzUTC = tzRegistry.getTimeZone(TimeZones.UTC_ID)!!

    private val vtzUTC = tzUTC.vTimeZone

    // Austria (Europa/Vienna) uses DST regularly
    private val vtzVienna = readTimeZone("Vienna.ics")

    // Pakistan (Asia/Karachi) used DST only in 2002, 2008 and 2009; no known future occurrences
    private val vtzKarachi = readTimeZone("Karachi.ics")

    // Somalia (Africa/Mogadishu) has never used DST
    private val vtzMogadishu = readTimeZone("Mogadishu.ics")

    private val minifier = VTimeZoneMinifier()
    

    @Test
    fun testMinifyTimezone_UTC() {
        // Keep the only observance for UTC.
        // DATE-TIME values in UTC are usually noted with ...Z and don't have a VTIMEZONE,
        // but it is allowed to write them as TZID=Etc/UTC.
        assertEquals(1, vtzUTC.observances.size)

        val minified = minifier.minify(vtzUTC, vtzUTC.zonedDateTime("2020-06-12T00:00"))

        assertEquals(1, minified.observances.size)
    }

    @Test
    fun testMinifyTimezone_removeObsoleteDstObservances() {
        // Remove obsolete observances when DST is used.
        assertEquals(6, vtzVienna.observances.size)
        // By default, the earliest observance is in 1893. We can drop that for events in 2020.
        assertEquals(LocalDateTime.parse("1893-04-01T00:00:00"), vtzVienna.observances.minOfOrNull { it.startDate.date })

        val minified = minifier.minify(vtzVienna, vtzVienna.zonedDateTime("2020-01-01"))

        assertEquals(2, minified.observances.size)
        // now earliest observance for STANDARD/DAYLIGHT is 1996/1981
        assertEquals(LocalDateTime.parse("1996-10-27T03:00:00"), minified.observances[0].startDate.date)
        assertEquals(LocalDateTime.parse("1981-03-29T02:00:00"), minified.observances[1].startDate.date)
    }

    @Test
    fun testMinifyTimezone_removeObsoleteObservances() {
        // Remove obsolete observances when DST is not used. Mogadishu had several time zone changes,
        // but now there is a simple offset without DST.
        assertEquals(4, vtzMogadishu.observances.size)

        val minified = minifier.minify(vtzMogadishu, vtzMogadishu.zonedDateTime("1961-10-01"))

        assertEquals(1, minified.observances.size)
    }

    @Test
    fun testMinifyTimezone_keepFutureObservances() {
        // Keep future observances.
        minifier.minify(vtzVienna, vtzVienna.zonedDateTime("1975-10-01")).let { minified ->
            val sortedStartDates = minified.observances
                .map { it.startDate.date }
                .sorted()
                .map { it.toString() }

            assertEquals(
                listOf("1916-04-30T23:00", "1916-10-01T01:00", "1981-03-29T02:00", "1996-10-27T03:00"),
                sortedStartDates
            )
        }

        minifier.minify(vtzKarachi, vtzKarachi.zonedDateTime("1961-10-01")).let { minified ->
            assertEquals(4, minified.observances.size)
        }

        minifier.minify(vtzKarachi, vtzKarachi.zonedDateTime("1975-10-01")).let { minified ->
            assertEquals(3, minified.observances.size)
        }

        minifier.minify(vtzMogadishu, vtzMogadishu.zonedDateTime("1931-10-01")).let { minified ->
            assertEquals(3, minified.observances.size)
        }
    }

    @Test
    fun testMinifyTimezone_keepDstWhenStartInDst() {
        // Keep DST when there are no obsolete observances, but start time is in DST.
        minifier.minify(vtzKarachi, vtzKarachi.zonedDateTime("2009-10-31")).let { minified ->
            assertEquals(2, minified.observances.size)
        }
    }

    @Test
    fun testMinifyTimezone_removeDstWhenNotUsedAnymore() {
        // Remove obsolete observances (including DST) when DST is not used anymore.
        minifier.minify(vtzKarachi, vtzKarachi.zonedDateTime("2010-01-01")).let { minified ->
            assertEquals(1, minified.observances.size)
        }
    }


    private fun readTimeZone(fileName: String): VTimeZone {
        javaClass.classLoader!!.getResourceAsStream("tz/$fileName").use { tzStream ->
            val cal = CalendarBuilder().build(tzStream)
            val vTimeZone = cal.getComponent<VTimeZone>(Component.VTIMEZONE).get()
            return vTimeZone
        }
    }

    private fun VTimeZone.zonedDateTime(dateTimeStr: String): ZonedDateTime {
        val dateTimeText = if ('T' in dateTimeStr) dateTimeStr else "${dateTimeStr}T00:00:00"
        val zoneId = ZoneId.of(timeZoneId.value)
        return ZonedDateTime.of(LocalDateTime.parse(dateTimeText), zoneId)
    }

}