/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.ical4android

import at.bitfire.DefaultTimezoneRule
import at.bitfire.dateTimeValue
import at.bitfire.dateValue
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Duration as JavaDuration
import java.time.Period as JavaPeriod

class TaskTest {

    @get:Rule
    val tzRule = DefaultTimezoneRule("Pacific/Auckland")

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")

    @Test
    fun testAllDay() {
        assertTrue(Task().isAllDay())

        // DTSTART has priority
        assertFalse(Task().apply {
            dtStart = DtStart(LocalDateTime.now())
        }.isAllDay())
        assertFalse(Task().apply {
            dtStart = DtStart(LocalDateTime.now())
            due = Due(LocalDate.now())
        }.isAllDay())
        assertTrue(Task().apply {
            dtStart = DtStart(LocalDate.now())
        }.isAllDay())
        assertTrue(Task().apply {
            dtStart = DtStart(LocalDate.now())
            due = Due(LocalDateTime.now())
        }.isAllDay())

        // if DTSTART is missing, DUE decides
        assertFalse(Task().apply {
            due = Due(LocalDateTime.now())
        }.isAllDay())
        assertTrue(Task().apply {
            due = Due(LocalDate.now())
        }.isAllDay())
    }

    @Test
    fun `end with due date`() {
        val due = Due(dateTimeValue("20260508T120000Z"))
        val task = Task(due = due)

        val result = task.end

        assertEquals(due, result)
    }

    @Test
    fun `end with start date being ZonedDateTime and duration being Duration`() {
        val task = Task(
            dtStart = DtStart(dateTimeValue("20260508T120000", tzVienna)),
            duration = Duration(JavaDuration.parse("PT1H"))
        )

        val result = task.end

        assertEquals(Due(dateTimeValue("20260508T110000Z")), result)
    }

    @Test
    fun `end with start date being ZonedDateTime and duration being Duration spanning DST change`() {
        val task = Task(
            dtStart = DtStart(dateTimeValue("20260329T010000", tzVienna)),
            duration = Duration(JavaDuration.parse("PT2H"))
        )

        val result = task.end

        assertEquals(Due(dateTimeValue("20260329T020000Z")), result)
    }

    @Test
    fun `end with start date being ZonedDateTime and duration being Period`() {
        val task = Task(
            dtStart = DtStart(dateTimeValue("20260508T120000", tzVienna)),
            duration = Duration(JavaPeriod.parse("P1D"))
        )

        val result = task.end

        assertEquals(Due(dateTimeValue("20260509T100000Z")), result)
    }

    @Test
    fun `end with start date being ZonedDateTime and duration being Period spanning DST change`() {
        val task = Task(
            dtStart = DtStart(dateTimeValue("20260329T010000", tzVienna)),
            duration = Duration(JavaPeriod.parse("P1D"))
        )

        val result = task.end

        assertEquals(Due(dateTimeValue("20260330T000000Z")), result)
    }

    @Test
    fun `end with start date being LocalDate and duration being Duration`() {
        val task = Task(
            dtStart = DtStart(dateValue("20260508")),
            duration = Duration(JavaDuration.parse("PT1H"))
        )

        val result = task.end

        assertEquals(Due(dateTimeValue("20260508T010000Z")), result)
    }

    @Test
    fun `end with start date being LocalDate and duration being Period`() {
        val task = Task(
            dtStart = DtStart(dateValue("20260508")),
            duration = Duration(JavaPeriod.parse("P1D"))
        )

        val result = task.end

        assertEquals(Due(dateTimeValue("20260509T000000Z")), result)
    }

    @Test
    fun `end with start date being LocalDateTime and duration being Duration`() {
        val task = Task(
            // floating DATE-TIME + system time zone (Pacific/Auckland): 20260508T000000Z
            dtStart = DtStart(dateTimeValue("20260508T120000")),
            duration = Duration(JavaDuration.parse("PT1H"))
        )

        val result = task.end

        assertEquals(Due(dateTimeValue("20260508T010000Z")), result)
    }

    @Test
    fun `end with start date being LocalDateTime and duration being Period`() {
        val task = Task(
            // floating DATE-TIME + system time zone (Pacific/Auckland): 20260508T000000Z
            dtStart = DtStart(dateTimeValue("20260508T120000")),
            duration = Duration(JavaPeriod.parse("P1D"))
        )

        val result = task.end

        assertEquals(Due(dateTimeValue("20260509T000000Z")), result)
    }

    @Test
    fun `end with start date being Instant and duration being Duration`() {
        val task = Task(
            dtStart = DtStart(dateTimeValue("20260508T120000Z")),
            duration = Duration(JavaDuration.parse("PT1H"))
        )

        val result = task.end

        assertEquals(Due(dateTimeValue("20260508T130000Z")), result)
    }

    @Test
    fun `end with start date being Instant and duration being Period`() {
        val task = Task(
            dtStart = DtStart(dateTimeValue("20260508T120000Z")),
            duration = Duration(JavaPeriod.parse("P1D"))
        )

        val result = task.end

        assertEquals(Due(dateTimeValue("20260509T120000Z")), result)
    }

    @Test
    fun `end with start date and without duration`() {
        val task = Task(
            dtStart = DtStart(dateTimeValue("20260508T120000Z")),
        )

        val result = task.end

        assertNull(result)
    }

    @Test
    fun `end with duration but no start date`() {
        val task = Task(
            duration = Duration(JavaDuration.parse("PT1H"))
        )

        val result = task.end

        assertNull(result)
    }
}
