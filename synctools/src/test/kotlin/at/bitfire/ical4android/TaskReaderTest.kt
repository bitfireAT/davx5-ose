/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.ical4android

import at.bitfire.dateTimeValue
import at.bitfire.dateValue
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.TimeZone
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.ProdId
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.immutable.ImmutableClazz
import net.fortuna.ical4j.model.property.immutable.ImmutableStatus
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStreamReader
import java.io.StringReader
import java.io.StringWriter
import java.nio.charset.Charset
import java.time.Duration
import java.time.temporal.Temporal

import net.fortuna.ical4j.model.property.Duration as ICalDuration

class TaskReaderTest {

    val testProdId = ProdId(javaClass.name)
    val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()!!
    val tzVienna: TimeZone = tzRegistry.getTimeZone("Europe/Vienna")!!

    @Test
    fun testCharsets() {
        var t = parseCalendarFile("latin1.ics", Charsets.ISO_8859_1)
        assertEquals("äöüß", t.summary)

        t = parseCalendarFile("utf8.ics")
        assertEquals("© äö — üß", t.summary)
        assertEquals("中华人民共和国", t.location)
    }

    @Test
    fun testDtStartDate_DueDateTime() {
        val t = parseCalendar("BEGIN:VCALENDAR\r\n" +
                "VERSION 2:0\r\n" +
                "BEGIN:VTODO\r\n" +
                "SUMMARY:DTSTART is DATE, but DUE is DATE-TIME\r\n" +
                "DTSTART;VALUE=DATE:20200731\r\n" +
                "DUE;TZID=Europe/Vienna:20200731T234600\r\n" +
                "END:VTODO\r\n" +
                "END:VCALENDAR\r\n")
        assertEquals("DTSTART is DATE, but DUE is DATE-TIME", t.summary)
        // rewrite DTSTART to DATE-TIME, too
        assertEquals(DtStart(dateTimeValue("20200731T000000", tzVienna)), t.dtStart)
        assertEquals(Due(dateTimeValue("20200731T234600", tzVienna)), t.due)
    }

    @Test
    fun testDtStartDateTime_DueDate() {
        val t = parseCalendar("BEGIN:VCALENDAR\r\n" +
                "VERSION 2:0\r\n" +
                "BEGIN:VTODO\r\n" +
                "SUMMARY:DTSTART is DATE-TIME, but DUE is DATE\r\n" +
                "DTSTART;TZID=Europe/Vienna:20200731T235510\r\n" +
                "DUE;VALUE=DATE:20200801\r\n" +
                "END:VTODO\r\n" +
                "END:VCALENDAR\r\n")
        assertEquals("DTSTART is DATE-TIME, but DUE is DATE", t.summary)
        // rewrite DTSTART to DATE-TIME, too
        assertEquals(DtStart(dateTimeValue("20200731T235510", tzVienna)), t.dtStart)
        assertEquals(Due(dateTimeValue("20200801T000000", tzVienna)), t.due)
    }

    @Test
    fun testDueBeforeDtStart() {
        val t = parseCalendar("BEGIN:VCALENDAR\r\n" +
                "VERSION 2:0\r\n" +
                "BEGIN:VTODO\r\n" +
                "SUMMARY:DUE before DTSTART\r\n" +
                "DTSTART;TZID=Europe/Vienna:20200731T234600\r\n" +
                "DUE;TZID=Europe/Vienna:20200731T123000\r\n" +
                "END:VTODO\r\n" +
                "END:VCALENDAR\r\n")
        assertEquals("DUE before DTSTART", t.summary)
        // invalid tasks with DUE before DTSTART: DTSTART should be set to null
        assertNull(t.dtStart)
        assertEquals(Due(dateTimeValue("20200731T123000", tzVienna)), t.due)
    }

    @Test
    fun testDurationWithoutDtStart() {
        val t = parseCalendar("BEGIN:VCALENDAR\r\n" +
                "VERSION 2:0\r\n" +
                "BEGIN:VTODO\r\n" +
                "SUMMARY:DURATION without DTSTART\r\n" +
                "DURATION:PT1H\r\n" +
                "END:VTODO\r\n" +
                "END:VCALENDAR\r\n")
        assertEquals("DURATION without DTSTART", t.summary)
        assertNull(t.dtStart)
        assertNull(t.duration)
    }

    @Test
    fun testEmptyPriority() {
        val t = parseCalendar("BEGIN:VCALENDAR\r\n" +
                "VERSION 2:0\r\n" +
                "BEGIN:VTODO\r\n" +
                "SUMMARY:Empty PRIORITY\r\n" +
                "PRIORITY:\r\n" +
                "END:VTODO\r\n" +
                "END:VCALENDAR\r\n")
        assertEquals("Empty PRIORITY", t.summary)
        assertEquals(0, t.priority)
    }


    @Test
    fun testSamples() {
        val t = regenerate(parseCalendarFile("rfc5545-sample1.ics"))
        assertEquals(2, t.sequence)
        assertEquals("uid4@example.com", t.uid)
        assertEquals("mailto:unclesam@example.com", t.organizer!!.value)
        assertEquals(Due(dateTimeValue("19980415T000000")), t.due)
        assertFalse(t.isAllDay())
        assertEquals(ImmutableStatus.VTODO_NEEDS_ACTION, t.status)
        assertEquals("Submit Income Taxes", t.summary)
    }

    @Test
    fun testAllFields() {
        // 1. parse the VTODO file
        // 2. generate a new VTODO file from the parsed code
        // 3. parse it again – so we can test parsing and generating at once
        var t = regenerate(parseCalendarFile("most-fields1.ics"))
        assertEquals(1, t.sequence)
        assertEquals("most-fields1@example.com", t.uid)
        assertEquals("Conference Room - F123, Bldg. 002", t.location)
        assertEquals("37.386013", t.geoPosition!!.latitude.toPlainString())
        assertEquals("-122.082932", t.geoPosition!!.longitude.toPlainString())
        assertEquals(
            "Meeting to provide technical review for \"Phoenix\" design.\nHappy Face Conference Room. Phoenix design team MUST attend this meeting.\nRSVP to team leader.",
            t.description
        )
        assertEquals("http://example.com/principals/jsmith", t.organizer!!.value)
        assertEquals("http://example.com/pub/calendars/jsmith/mytime.ics", t.url)
        assertEquals(1, t.priority)
        assertEquals(ImmutableClazz.CONFIDENTIAL, t.classification)
        assertEquals(ImmutableStatus.VTODO_IN_PROCESS, t.status)
        assertEquals(25, t.percentComplete)
        assertEquals(DtStart(dateValue("20100101")), t.dtStart)
        assertEquals(Due(dateValue("20101001")), t.due)
        assertTrue(t.isAllDay())

        assertEquals(RRule<Temporal>("FREQ=YEARLY;INTERVAL=2"), t.rRule)
        assertEquals(2, t.exDates.size)
        assertTrue(t.exDates.contains(ExDate(ParameterList(listOf(Value.DATE)), DateList(dateValue("20120101")))))
        assertTrue(t.exDates.contains(ExDate(ParameterList(listOf(Value.DATE)), DateList(dateValue("20140101"), dateValue("20180101")))))
        assertEquals(2, t.rDates.size)
        assertTrue(t.rDates.contains(RDate(ParameterList(listOf(Value.DATE)), DateList(dateValue("20100310"), dateValue("20100315")))))
        assertTrue(t.rDates.contains(RDate(ParameterList(listOf(Value.DATE)), DateList(dateValue("20100810")))))

        assertEquals(828106200000L, t.createdAt)
        assertEquals(840288600000L, t.lastModified)

        assertArrayEquals(arrayOf("Test", "Sample"), t.categories.toArray())

        val (sibling) = t.relatedTo
        assertEquals("most-fields2@example.com", sibling.value)
        assertEquals(RelType.SIBLING, sibling.getRequiredParameter<RelType>(Parameter.RELTYPE))

        val (unknown) = t.unknownProperties
        assertEquals("X-UNKNOWN-PROP", unknown.name)
        assertEquals("xxx", unknown.getRequiredParameter<Parameter>("param1").value)
        assertEquals("Unknown Value", unknown.value)

        // other file
        t = regenerate(parseCalendarFile("most-fields2.ics"))
        assertEquals("most-fields2@example.com", t.uid)
        assertEquals(DtStart(dateTimeValue("20100101T101010Z")), t.dtStart)
        assertEquals(
            ICalDuration(Duration.ofSeconds(4 * 86400 + 3 * 3600 + 2 * 60 + 1)),
            t.duration
        )
        assertTrue(t.unknownProperties.isEmpty())
    }


    /* helpers */

    private fun parseCalendar(iCalendar: String): Task =
        TaskReader().readTasks(StringReader(iCalendar)).first()

    private fun parseCalendarFile(fname: String, charset: Charset = Charsets.UTF_8): Task {
        javaClass.classLoader!!.getResourceAsStream("tasks/$fname").use { stream ->
            return TaskReader().readTasks(InputStreamReader(stream, charset)).first()
        }
    }

    private fun regenerate(t: Task): Task {
        val icalWriter = StringWriter()
        TaskWriter(testProdId).write(t, icalWriter)
        return TaskReader().readTasks(StringReader(icalWriter.toString())).first()
    }
}