/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.icalendar

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtStart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.time.ZonedDateTime

class SystemAwareTimeZoneRegistryTest {

    @Test
    fun `VTIMEZONE without STANDARD and DAYLIGHT sub-components`() {
        assertTrue(TimeZoneRegistryFactory.getInstance() is SystemAwareTimeZoneRegistry.Factory)

        val reader = StringReader(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Example Corp.//CalDAV Client//EN
            BEGIN:VTIMEZONE
            TZID:UTC
            END:VTIMEZONE
            BEGIN:VEVENT
            DTSTAMP:20260529T095200Z
            UID:bc295665-5b3b-11f1-9a52-d843aea66ff2
            DTSTART;TZID=UTC:20260528T120000
            END:VEVENT
            END:VCALENDAR
            """.trimIndent()
        )

        val calendar = CalendarBuilder().build(reader)

        assertNotNull(calendar)
    }

    @Test
    fun `VTIMEZONE with system-known TZID should not produce ical4j-local zone`() {
        val reader = StringReader(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Example Corp.//CalDAV Client//EN
            BEGIN:VTIMEZONE
            TZID:Europe/Berlin
            BEGIN:STANDARD
            DTSTART:19701025T030000
            TZOFFSETFROM:+0200
            TZOFFSETTO:+0100
            END:STANDARD
            BEGIN:DAYLIGHT
            DTSTART:19700329T020000
            TZOFFSETFROM:+0100
            TZOFFSETTO:+0200
            END:DAYLIGHT
            END:VTIMEZONE
            BEGIN:VEVENT
            DTSTAMP:20260101T000000Z
            UID:test-system-tzid@example.com
            DTSTART;TZID=Europe/Berlin:20260101T120000
            END:VEVENT
            END:VCALENDAR
            """.trimIndent()
        )

        val calendar = CalendarBuilder().build(reader)
        val event = calendar.getComponents<VEvent>(VEvent.VEVENT).first()
        val dtStart = event.getProperty<DtStart<*>>(DtStart.DTSTART).get()
        val date = dtStart.date

        assertTrue(date is ZonedDateTime)
        assertEquals("Europe/Berlin", (date as ZonedDateTime).zone.id)
    }

    @Test
    fun `VTIMEZONE with unknown TZID should produce ical4j-local zone`() {
        val reader = StringReader(
            """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Example Corp.//CalDAV Client//EN
            BEGIN:VTIMEZONE
            TZID:My/CustomZone
            BEGIN:STANDARD
            DTSTART:19701025T030000
            TZOFFSETFROM:+0200
            TZOFFSETTO:+0100
            END:STANDARD
            END:VTIMEZONE
            BEGIN:VEVENT
            DTSTAMP:20260101T000000Z
            UID:test-custom-tzid@example.com
            DTSTART;TZID=My/CustomZone:20260101T120000
            END:VEVENT
            END:VCALENDAR
            """.trimIndent()
        )

        val calendar = CalendarBuilder().build(reader)
        val event = calendar.getComponents<VEvent>(VEvent.VEVENT).first()
        val dtStart = event.getProperty<DtStart<*>>(DtStart.DTSTART).get()
        val date = (dtStart.date as ZonedDateTime)

        assertTrue(
            "ZonedDateTime should have a ical4j-parsed custom ZoneId",
            date.zone.id.startsWith("ical4j-local-")
        )
    }
}
