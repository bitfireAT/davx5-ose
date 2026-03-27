/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.util

import org.junit.Assert
import org.junit.Test

class ICalendarUtilsTest {

    @Test
    fun testGetVTimeZoneId_Valid() {
        // Test with a valid VTimeZone definition
        val vTimeZoneDef = "BEGIN:VTIMEZONE\r\n" +
                "TZID:Europe/Vienna\r\n" +
                "BEGIN:STANDARD\r\n" +
                "DTSTART:19701025T030000\r\n" +
                "TZOFFSETFROM:+0200\r\n" +
                "TZOFFSETTO:+0100\r\n" +
                "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=10\r\n" +
                "END:STANDARD\r\n" +
                "BEGIN:DAYLIGHT\r\n" +
                "DTSTART:19700329T020000\r\n" +
                "TZOFFSETFROM:+0100\r\n" +
                "TZOFFSETTO:+0200\r\n" +
                "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=3\r\n" +
                "END:DAYLIGHT\r\n" +
                "END:VTIMEZONE\r\n"
        val result = ICalendarUtils.getVTimeZoneId(vTimeZoneDef)
        Assert.assertEquals("Europe/Vienna", result)
    }

    @Test
    fun testGetVTimeZoneId_Invalid() {
        // Test with empty string
        Assert.assertNull(ICalendarUtils.getVTimeZoneId(""))

        // Test with malformed VTimeZone (missing BEGIN/END)
        val malformed = "TZID:America/New_York\r\n"
        Assert.assertNull(ICalendarUtils.getVTimeZoneId(malformed))

        // Test with incomplete VTimeZone
        val incomplete = "BEGIN:VTIMEZONE\r\n" +
                "TZID:Asia/Tokyo\r\n"
        Assert.assertNull(ICalendarUtils.getVTimeZoneId(incomplete))
    }

    @Test
    fun testGetVTimeZoneId_MissingTZID() {
        // Test with VTimeZone missing TZID property
        val noTzId = "BEGIN:VTIMEZONE\r\n" +
                "BEGIN:STANDARD\r\n" +
                "DTSTART:19701025T030000\r\n" +
                "TZOFFSETFROM:+0200\r\n" +
                "TZOFFSETTO:+0100\r\n" +
                "END:STANDARD\r\n" +
                "END:VTIMEZONE\r\n"

        Assert.assertNull(ICalendarUtils.getVTimeZoneId(noTzId))
    }

}