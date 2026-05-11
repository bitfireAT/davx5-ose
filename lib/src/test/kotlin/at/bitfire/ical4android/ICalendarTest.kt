/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.property.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.StringReader
import kotlin.jvm.optionals.getOrNull

class ICalendarTest {

	@Test
	fun testFromReader_calendarProperties() {
		val calendar = ICalendar.fromReader(
            StringReader(
                "BEGIN:VCALENDAR\n" +
                        "VERSION:2.0\n" +
                        "METHOD:PUBLISH\n" +
                        "PRODID:something\n" +
                        "X-WR-CALNAME:Some Calendar\n" +
                        "COLOR:darkred\n" +
                        "X-APPLE-CALENDAR-COLOR:#123456\n" +
                        "END:VCALENDAR"
            )
		)
		assertEquals("Some Calendar", calendar.getProperty<Property>(ICalendar.CALENDAR_NAME).getOrNull()?.value)
        assertEquals("darkred", calendar.getProperty<Property>(Color.PROPERTY_NAME).getOrNull()?.value)
        assertEquals("#123456", calendar.getProperty<Property>(ICalendar.CALENDAR_COLOR).getOrNull()?.value)
	}

	@Test
	fun testFromReader_invalidProperty() {
		// The GEO property is invalid and should be ignored.
		// The calendar is however parsed without exception.
        assertNotNull(
            ICalendar.fromReader(
                StringReader(
                    "BEGIN:VCALENDAR\n" +
                            "PRODID:something\n" +
                            "VERSION:2.0\n" +
                            "BEGIN:VEVENT\n" +
                            "UID:xxx@example.com\n" +
                            "SUMMARY:Example Event with invalid GEO property\n" +
                            "GEO:37.7957246371765\n" +
                            "END:VEVENT\n" +
                            "END:VCALENDAR"
                )
            )
        )
	}


    @Test
    fun testTimezoneDefToTzId_Valid() {
        assertEquals(
            "US-Eastern", ICalendar.timezoneDefToTzId(
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
        assertNull(ICalendar.timezoneDefToTzId("/* invalid content */"))

        // time zone without TZID
        assertNull(
            ICalendar.timezoneDefToTzId(
                "BEGIN:VCALENDAR\n" +
                        "PRODID:-//Inverse inc./SOGo 2.2.10//EN\n" +
                        "VERSION:2.0\n" +
                        "END:VCALENDAR"
            )
        )
    }

}