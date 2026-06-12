/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.icalendar

import at.bitfire.dateTimeValue
import at.bitfire.dateValue
import net.fortuna.ical4j.model.property.DateProperty
import net.fortuna.ical4j.model.property.DtStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Ical4jHelpersTest {

    @Test
    fun testTimezoneDefToTzId_Valid() {
        assertEquals(
            "US-Eastern", timezoneDefToTzId(
                "BEGIN:VCALENDAR\n" +
                        "PRODID:-//Example Corp.//CalDAV Client//EN\n" +
                        "VERSION:2.0\n" +
                        "BEGIN:VTIMEZONE\n" +
                        "TZID:US-Eastern\n" +
                        "LAST-MODIFIED:19870101T000000Z\n" +
                        "BEGIN:STANDARD\n" +
                        "DTSTART:19671029T020000\n" +
                        "RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=10\n" +
                        "TZOFFSETFROM:-0400\n" +
                        "TZOFFSETTO:-0500\n" +
                        "TZNAME:Eastern Standard Time (US &amp; Canada)\n" +
                        "END:STANDARD\n" +
                        "BEGIN:DAYLIGHT\n" +
                        "DTSTART:19870405T020000\n" +
                        "RRULE:FREQ=YEARLY;BYDAY=1SU;BYMONTH=4\n" +
                        "TZOFFSETFROM:-0500\n" +
                        "TZOFFSETTO:-0400\n" +
                        "TZNAME:Eastern Daylight Time (US &amp; Canada)\n" +
                        "END:DAYLIGHT\n" +
                        "END:VTIMEZONE\n" +
                        "END:VCALENDAR"
            )
        )
    }

    @Test
    fun testTimezoneDefToTzId_Invalid() {
        // invalid time zone
        assertNull(timezoneDefToTzId("/* invalid content */"))

        // time zone without TZID
        assertNull(
            timezoneDefToTzId(
                "BEGIN:VCALENDAR\n" +
                        "PRODID:-//Inverse inc./SOGo 2.2.10//EN\n" +
                        "VERSION:2.0\n" +
                        "END:VCALENDAR"
            )
        )
    }

    @Test
    fun testIsAllDay_DateProperty() {
        assertTrue(DtStart(dateValue("20200101")).isAllDay())
        assertFalse(DtStart(dateTimeValue("20200101T010203Z")).isAllDay())
        assertFalse((null as DateProperty<*>?).isAllDay())
    }

}
