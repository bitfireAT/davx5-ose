/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.icalendar

import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class TimeZoneRegistryFactoryWorkaroundTest {

    @Test
    fun `VTIMEZONE without STANDARD and DAYLIGHT sub-components`() {
        assertTrue(TimeZoneRegistryFactory.getInstance() is TimeZoneRegistryFactoryWorkaround)

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
}
