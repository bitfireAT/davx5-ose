/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.icalendar

import at.bitfire.dateTimeValue
import at.bitfire.synctools.icalendar.validation.ICalPreprocessor
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.data.CalendarOutputter
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.TemporalAmountAdapter
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.component.VTimeZone
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.transform.compliance.DatePropertyRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter
import java.time.Duration
import java.time.Period
import java.time.ZoneOffset
import java.time.ZonedDateTime

class Ical4jTest {

    private val tzReg = TimeZoneRegistryFactory.getInstance().createRegistry()

    @Test
    fun `ATTENDEE with EMAIL parameter`() {
        // https://github.com/ical4j/ical4j/issues/418
        val event = ICalendarParser().parse(
            StringReader(
                "BEGIN:VCALENDAR\n" +
                        "VERSION:2.0\n" +
                        "BEGIN:VEVENT\n" +
                        "SUMMARY:Test\n" +
                        "DTSTART;VALUE=DATE:20200702\n" +
                        "ATTENDEE;EMAIL=attendee1@example.virtual:sample:attendee1\n" +
                        "END:VEVENT\n" +
                        "END:VCALENDAR"
            )
        ).getComponent<VEvent>(Component.VEVENT).get()
        val attendee = event.getRequiredProperty<Attendee>(Property.ATTENDEE)
        assertEquals("attendee1@example.virtual", attendee.getRequiredParameter<Email>(Parameter.EMAIL).value)
    }

    @Test
    fun `DTSTART in America_Asuncion from KOrganizer`() {
        // See https://github.com/bitfireAT/synctools/issues/113
        val vtzFromKOrganizer = "BEGIN:VCALENDAR\n" +
                "CALSCALE:GREGORIAN\n" +
                "VERSION:2.0\n" +
                "PRODID:-//K Desktop Environment//NONSGML KOrganizer 6.5.0 (25.08.0)//EN\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:America/Asuncion\n" +
                "BEGIN:STANDARD\n" +
                "TZNAME:-03\n" +
                "TZOFFSETFROM:-0300\n" +
                "TZOFFSETTO:-0300\n" +
                "DTSTART:19700101T000000\n" +
                "END:STANDARD\n" +
                "END:VTIMEZONE\n" +
                "BEGIN:VEVENT\n" +
                "DTSTAMP:20250828T233827Z\n" +
                "CREATED:20250828T233750Z\n" +
                "UID:e5d424b9-d3f6-4ee0-bf95-da7537fca1fe\n" +
                "LAST-MODIFIED:20250828T233827Z\n" +
                "SUMMARY:Test Timezones\n" +
                "RRULE:FREQ=WEEKLY;COUNT=3;BYDAY=TH\n" +
                "DTSTART;TZID=America/Asuncion:20250828T130000\n" +
                "DTEND;TZID=America/Asuncion:20250828T133000\n" +
                "TRANSP:OPAQUE\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR"
        val iCalFromKOrganizer = CalendarBuilder().build(StringReader(vtzFromKOrganizer))
        ICalPreprocessor().preprocessCalendar(iCalFromKOrganizer)
        val vEvent = iCalFromKOrganizer.getComponent<VEvent>(Component.VEVENT).get()
        val dtStart = vEvent.requireDtStart<ZonedDateTime>()
        assertEquals(ZoneOffset.ofHours(-3), ZoneOffset.from(dtStart.date))
    }

    @Test
    fun `PRODID is not folded when exactly max line length`() {
        // https://github.com/ical4j/ical4j/issues/832
        val calendar = Calendar()
            .add<Calendar>(ProdId("01234567890123456789012345678901234567890123456789012345678901234567"))

        val writer = StringWriter()
        CalendarOutputter().output(calendar, writer)
        assertEquals("BEGIN:VCALENDAR\r\n" +
                "PRODID:01234567890123456789012345678901234567890123456789012345678901234567\r\n" +
                "END:VCALENDAR\r\n", writer.toString())
    }

    @Test
    fun `TemporalAmountAdapter durationToString drops minutes`() {
        // https://github.com/ical4j/ical4j/issues/420
        assertEquals("P1DT1H4M", TemporalAmountAdapter.parse("P1DT1H4M").toString())
    }

    @Test(expected = AssertionError::class)
    fun `TemporalAmountAdapter months`() {
        // https://github.com/ical4j/ical4j/issues/419
        // A month usually doesn't have 4 weeks = 4*7 days = 28 days (except February in non-leap years).
        assertNotEquals("P4W", TemporalAmountAdapter(Period.ofMonths(1)).toString())
    }

    @Test(expected = AssertionError::class)
    fun `TemporalAmountAdapter year`() {
        // https://github.com/ical4j/ical4j/issues/419
        // A year has 365 or 366 days, but never 52 weeks = 52*7 days = 364 days.
        assertNotEquals("P52W", TemporalAmountAdapter(Period.ofYears(1)).toString())
    }

    @Test
    fun `TZ Darwin`() {
        // https://github.com/ical4j/ical4j/issues/491
        val darwin = tzReg.getTimeZone("Australia/Darwin")
        val date = dateTimeValue("20210326T103000", darwin)
        val timestamp = date.toInstant().toEpochMilli()

        val offset = darwin.getOffset(timestamp)

        assertEquals(
            Duration.ofHours(9) + Duration.ofMinutes(30),
            Duration.ofMillis(offset.toLong())
        )
    }

    @Test
    fun `TZ Dublin with negative DST`() {
        // https://github.com/ical4j/ical4j/issues/493
        // fixed by enabling net.fortuna.ical4j.timezone.offset.negative_dst_supported in ical4j.properties
        val vtzFromGoogle = "BEGIN:VCALENDAR\n" +
                "CALSCALE:GREGORIAN\n" +
                "VERSION:2.0\n" +
                "PRODID:-//Google Inc//Google Calendar 70.9054//EN\n" +
                "BEGIN:VTIMEZONE\n" +
                "TZID:Europe/Dublin\n" +
                "BEGIN:STANDARD\n" +
                "TZOFFSETFROM:+0000\n" +
                "TZOFFSETTO:+0100\n" +
                "TZNAME:IST\n" +
                "DTSTART:19700329T010000\n" +
                "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU\n" +
                "END:STANDARD\n" +
                "BEGIN:DAYLIGHT\n" +
                "TZOFFSETFROM:+0100\n" +
                "TZOFFSETTO:+0000\n" +
                "TZNAME:GMT\n" +
                "DTSTART:19701025T020000\n" +
                "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU\n" +
                "END:DAYLIGHT\n" +
                "END:VTIMEZONE\n" +
                "BEGIN:VEVENT\n" +
                "DTSTAMP:20260320T150800Z\n" +
                "CREATED:20260320T150800Z\n" +
                "UID:d46ddc27-7a7b-4004-8ba1-04f4de4d2ef3\n" +
                "LAST-MODIFIED:20260320T150800Z\n" +
                "SUMMARY:Test VTimezone\n" +
                "DTSTART;TZID=Europe/Dublin:20210108T151500\n" +
                "DTEND;TZID=Europe/Dublin:20210108T153000\n" +
                "END:VEVENT\n" +
                "END:VCALENDAR"
        val iCalFromGoogle = CalendarBuilder().build(StringReader(vtzFromGoogle))
        val event = iCalFromGoogle.getComponent<VEvent>(Component.VEVENT).get()
        val startDate = event.requireDtStart<ZonedDateTime>().date
        assertEquals(dateTimeValue("20210108T151500"), startDate.toLocalDateTime())
    }

    @Test
    fun `TZ Karachi`() {
        // https://github.com/ical4j/ical4j/issues/475
        val karachi = tzReg.getTimeZone("Asia/Karachi")
        val date = dateTimeValue("20210106T200000", karachi)
        val timestamp = date.toInstant().toEpochMilli()

        val offset = karachi.getOffset(timestamp)

        assertEquals(
            Duration.ofHours(5),
            Duration.ofMillis(offset.toLong())
        )
    }

    @Test
    fun `TZID with parentheses and space`() {
        /* DTSTART;TZID="...":...  is formally invalid because RFC 5545 only allows tzidparam to be a
        paramtext and not a quoted-string for an unknown reason (see also https://www.rfc-editor.org/errata/eid5505).
        Some generators don't know that and still use DQUOTE. Doing so caused a problem with DAVx5.
        This test verifies that ical4j is capable to parse such TZIDs. */
        val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
        val cal = CalendarBuilder(tzRegistry).build(
            StringReader("BEGIN:VCALENDAR\n" +
                    "VERSION:2.0\n" +
                    "BEGIN:VTIMEZONE\n" +
                    "TZID:(GMT -05:00)\n" +
                    "BEGIN:STANDARD\n" +
                    "DTSTART:19700101T020000\n" +
                    "TZOFFSETFROM:-0400\n" +
                    "TZOFFSETTO:-0500\n" +
                    "RRULE:FREQ=YEARLY;BYDAY=1SU;BYMONTH=11;WKST=SU\n" +
                    "END:STANDARD\n" +
                    "BEGIN:DAYLIGHT\n" +
                    "DTSTART:19700101T020000\n" +
                    "TZOFFSETFROM:-0500\n" +
                    "TZOFFSETTO:-0400\n" +
                    "RRULE:FREQ=YEARLY;BYDAY=2SU;BYMONTH=3;WKST=SU\n" +
                    "END:DAYLIGHT\n" +
                    "END:VTIMEZONE\n" +
                    "BEGIN:VEVENT\n" +
                    "DTSTART;TZID=\"(GMT -05:00)\":20250124T190000\n" +     // technically invalid TZID parameter
                    "DTEND;TZID=\"(GMT -05:00)\":20250124T203000\n" +       // technically invalid TZID parameter
                    "SUMMARY:Special timezone definition\n" +
                    "END:VEVENT\n" +
                    "END:VCALENDAR\n"
            )
        )
        val event = cal.getComponent<VEvent>(Component.VEVENT).get()
        val tzGMT5 = tzRegistry.getTimeZone("(GMT -05:00)")
        assertNotNull(tzGMT5)
        val dtStart = event.requireDtStart<ZonedDateTime>()
        val dtEnd = event.getEndDate<ZonedDateTime>(false).get()
        assertEquals(dateTimeValue("20250124T190000"), dtStart.date.toLocalDateTime())
        assertEquals(dateTimeValue("20250124T203000"), dtEnd.date.toLocalDateTime())
        assertEquals(dateTimeValue("20250125T000000Z"), dtStart.date.toInstant())
        assertEquals(dateTimeValue("20250125T013000Z"), dtEnd.date.toInstant())
    }

    @Test
    fun `DatePropertyRule with TZID value that can not be mapped should adjust time`() {
        // https://github.com/ical4j/ical4j/issues/868
        val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
        val calendar = CalendarBuilder(tzRegistry).build(
            StringReader(
                """
                BEGIN:VCALENDAR
                VERSION:2.0
                BEGIN:VTIMEZONE
                TZID:can-not-map
                BEGIN:STANDARD
                DTSTART:19700101T020000
                TZOFFSETFROM:-0400
                TZOFFSETTO:-0500
                RRULE:FREQ=YEARLY;BYDAY=1SU;BYMONTH=11;WKST=SU
                END:STANDARD
                BEGIN:DAYLIGHT
                DTSTART:19700101T020000
                TZOFFSETFROM:-0500
                TZOFFSETTO:-0400
                RRULE:FREQ=YEARLY;BYDAY=2SU;BYMONTH=3;WKST=SU
                END:DAYLIGHT
                END:VTIMEZONE
                BEGIN:VEVENT
                DTSTART;TZID=can-not-map:20260324T100000
                DURATION:PT1H
                SUMMARY:Timezone definition that can't be mapped to an ical4j time zone
                END:VEVENT
                END:VCALENDAR
                """.trimIndent().replace("\n", "\r\n")
            )
        )
        val event = calendar.getComponent<VEvent>(Component.VEVENT).orElseThrow()
        val dtStart = event.requireDtStart<ZonedDateTime>()

        val result = DatePropertyRule().apply(dtStart)

        //assertEquals(DtStart(dateTimeValue("20260324T140000Z")), result)

        // We expect the date to be converted to UTC (see above). However, currently only the
        // TZID parameter is removed, essentially converting the value to a floating date-time.
        // This assertion will fail (and the one commented out above pass) once this is fixed in
        // ical4j.
        assertEquals(DtStart(dateTimeValue("20260324T100000")), result)
    }

    @Test
    fun `VTIMEZONE containing RDATE with PERIOD should parse without exception`() {
        // https://github.com/bitfireAT/synctools/issues/103
        val calendar = CalendarBuilder().build(
            StringReader(
                "BEGIN:VCALENDAR\n" +
                        "VERSION:2.0\n" +
                        "BEGIN:VTIMEZONE\n" +
                        "TZID:Europe/Berlin\n" +
                        "X-TZINFO:Europe/Berlin[2025b]\n" +
                        "BEGIN:STANDARD\n" +
                        "DTSTART:18930401T000000\n" +
                        "RDATE;VALUE=PERIOD:18930401T000000/18930402T000000\n" +
                        "TZNAME:Europe/Berlin(STD)\n" +
                        "TZOFFSETFROM:+005328\n" +
                        "TZOFFSETTO:+0100\n" +
                        "END:STANDARD\n" +
                        "END:VTIMEZONE\n" +
                        "BEGIN:VEVENT\n" +
                        "UID:3b3c1b0e-e74c-48ef-ada8-33afc543648d\n" +
                        "DTSTART;TZID=Europe/Berlin:20250917T122000\n" +
                        "DTEND;TZID=Europe/Berlin:20250917T124500\n" +
                        "END:VEVENT\n" +
                        "END:VCALENDAR"
            )
        )

        assertTrue(calendar.getComponent<VTimeZone>(Component.VTIMEZONE).isPresent)
    }

}