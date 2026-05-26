/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.techbee.jtx.JtxContract
import at.techbee.jtx.JtxContract.JtxICalObject.TZ_ALLDAY
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.TzId
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class RecurrenceFieldsHandlerTest {

    private val handler = RecurrenceFieldsHandler()

    // ===== Main object tests =====

    @Test
    fun `No recurrence fields`() {
        val from = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = from, main = from, to = output)

        assertNull(output.getProperty<RRule<*>>(Property.RRULE).getOrNull())
        assertNull(output.getProperty<RDate<*>>(Property.RDATE).getOrNull())
        assertNull(output.getProperty<ExDate<*>>(Property.EXDATE).getOrNull())
    }

    @Test
    fun `RRULE is mapped to main`() {
        val from = Entity(contentValuesOf(
            JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5"
        ))
        val output = VToDo()

        handler.process(from = from, main = from, to = output)

        assertEquals(
            "FREQ=DAILY;COUNT=5",
            output.getProperty<RRule<*>>(Property.RRULE).getOrNull()?.value
        )
    }

    @Test
    fun `RDATE with UTC timestamps`() {
        // 1779451200000 ms = 2026-05-22T12:00:00Z
        val from = Entity(contentValuesOf(
            JtxContract.JtxICalObject.DTSTART_TIMEZONE to ZoneOffset.UTC.id,
            JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
            JtxContract.JtxICalObject.RDATE to "1779451200000"
        ))
        val output = VToDo()

        handler.process(from = from, main = from, to = output)

        val rdate = output.getProperty<RDate<*>>(Property.RDATE).getOrNull()
        assertNotNull(rdate)
        assertEquals(1, rdate?.dates?.size)
        assertEquals("20260522T120000Z", rdate?.value)
    }

    @Test
    fun rdateWithDtstartTimezoneUTC_shouldSerializeSameAsZ() {
        // 1779451200000 ms = 2026-05-22T12:00:00Z
        val from = Entity(contentValuesOf(
            JtxContract.JtxICalObject.DTSTART_TIMEZONE to "UTC",
            JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
            JtxContract.JtxICalObject.RDATE to "1779451200000"
        ))
        val output = VToDo()

        handler.process(from = from, main = from, to = output)

        val rdate = output.getProperty<RDate<*>>(Property.RDATE).getOrNull()
        assertNotNull(rdate)
        assertEquals(1, rdate?.dates?.size)
        // Should serialize with trailing Z (no TZID parameter), same as ZoneOffset.UTC.id
        assertEquals("20260522T120000Z", rdate?.value)
        assertNull(rdate?.getParameter<TzId>(Parameter.TZID)?.getOrNull())
    }

    @Test
    fun `RDATE with all-day timestamps`() {
        // 1779408000000 ms = 2026-05-22T00:00:00Z → all-day LocalDate 2026-05-22
        val from = Entity(contentValuesOf(
            JtxContract.JtxICalObject.DTSTART_TIMEZONE to TZ_ALLDAY,
            JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
            JtxContract.JtxICalObject.RDATE to "1779408000000"
        ))
        val output = VToDo()

        handler.process(from = from, main = from, to = output)

        val rdate = output.getProperty<RDate<*>>(Property.RDATE).getOrNull()
        assertNotNull(rdate)
        assertEquals(1, rdate?.dates?.size)
        assertEquals(LocalDate.of(2026, 5, 22), rdate?.dates?.first())
        assertEquals(Value.DATE, rdate?.getParameter<Value>(Parameter.VALUE)?.getOrNull())
    }

    @Test
    fun `RDATE with named timezone`() {
        // 1779444000000 ms = 2026-05-22T10:00:00Z = 2026-05-22T12:00:00+02:00[Europe/Vienna]
        val from = Entity(contentValuesOf(
            JtxContract.JtxICalObject.DTSTART_TIMEZONE to "Europe/Vienna",
            JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
            JtxContract.JtxICalObject.RDATE to "1779444000000"
        ))
        val output = VToDo()

        handler.process(from = from, main = from, to = output)

        val rdate = output.getProperty<RDate<*>>(Property.RDATE).getOrNull()
        assertNotNull(rdate)
        assertEquals(1, rdate?.dates?.size)
        assertEquals("20260522T120000", rdate?.value)
        assertEquals("Europe/Vienna", rdate?.getParameter<TzId>(Parameter.TZID)?.getOrNull()?.value)
    }

    @Test
    fun `RDATE with floating timestamps`() {
        // null DTSTART_TIMEZONE → floating LocalDateTime
        val from = Entity(contentValuesOf(
            JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
            JtxContract.JtxICalObject.RDATE to "1779451200000"
        ))
        val output = VToDo()

        handler.process(from = from, main = from, to = output)

        val rdate = output.getProperty<RDate<*>>(Property.RDATE).getOrNull()
        assertNotNull(rdate)
        assertEquals(1, rdate?.dates?.size)
        // Floating datetimes have no TZID parameter
        assertNull(rdate?.getParameter<TzId>(Parameter.TZID)?.getOrNull())
    }

    @Test
    fun `Multiple RDATE timestamps produce single RDate property with all dates`() {
        // 1779451200000 = 2026-05-22T12:00:00Z, 1779624000000 = 2026-05-24T12:00:00Z
        val from = Entity(contentValuesOf(
            JtxContract.JtxICalObject.DTSTART_TIMEZONE to ZoneOffset.UTC.id,
            JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
            JtxContract.JtxICalObject.RDATE to "1779451200000,1779624000000"
        ))
        val output = VToDo()

        handler.process(from = from, main = from, to = output)

        val rdate = output.getProperty<RDate<*>>(Property.RDATE).getOrNull()
        assertNotNull(rdate)
        assertEquals(2, rdate?.dates?.size)
    }

    @Test
    fun `RDATE with empty timestamps is ignored`() {
        val from = Entity(contentValuesOf(
            JtxContract.JtxICalObject.DTSTART_TIMEZONE to ZoneOffset.UTC.id,
            JtxContract.JtxICalObject.RDATE to ""
        ))
        val output = VToDo()

        handler.process(from = from, main = from, to = output)

        assertNull(output.getProperty<RDate<*>>(Property.RDATE).getOrNull())
    }

    @Test
    fun `EXDATE with UTC timestamps`() {
        // 1779451200000 ms = 2026-05-22T12:00:00Z
        val from = Entity(contentValuesOf(
            JtxContract.JtxICalObject.DTSTART_TIMEZONE to ZoneOffset.UTC.id,
            JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
            JtxContract.JtxICalObject.EXDATE to "1779451200000"
        ))
        val output = VToDo()

        handler.process(from = from, main = from, to = output)

        val exdate = output.getProperty<ExDate<*>>(Property.EXDATE).getOrNull()
        assertNotNull(exdate)
        assertEquals(1, exdate?.dates?.size)
        assertEquals("20260522T120000Z", exdate?.value)
    }

    @Test
    fun exdateWithDtstartTimezoneUTC_shouldSerializeSameAsZ() {
        // 1779451200000 ms = 2026-05-22T12:00:00Z
        val from = Entity(contentValuesOf(
            JtxContract.JtxICalObject.DTSTART_TIMEZONE to "UTC",
            JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
            JtxContract.JtxICalObject.EXDATE to "1779451200000"
        ))
        val output = VToDo()

        handler.process(from = from, main = from, to = output)

        val exdate = output.getProperty<ExDate<*>>(Property.EXDATE).getOrNull()
        assertNotNull(exdate)
        assertEquals(1, exdate?.dates?.size)
        // Should serialize with trailing Z (no TZID parameter), same as ZoneOffset.UTC.id
        assertEquals("20260522T120000Z", exdate?.value)
        assertNull(exdate?.getParameter<TzId>(Parameter.TZID)?.getOrNull())
    }

    @Test
    fun `EXDATE with all-day timestamps`() {
        // 1779408000000 ms = 2026-05-22T00:00:00Z → all-day LocalDate 2026-05-22
        val from = Entity(contentValuesOf(
            JtxContract.JtxICalObject.DTSTART_TIMEZONE to TZ_ALLDAY,
            JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
            JtxContract.JtxICalObject.EXDATE to "1779408000000"
        ))
        val output = VToDo()

        handler.process(from = from, main = from, to = output)

        val exdate = output.getProperty<ExDate<*>>(Property.EXDATE).getOrNull()
        assertNotNull(exdate)
        assertEquals(1, exdate?.dates?.size)
        assertEquals(LocalDate.of(2026, 5, 22), exdate?.dates?.first())
        assertEquals(Value.DATE, exdate?.getParameter<Value>(Parameter.VALUE)?.getOrNull())
    }

    @Test
    fun `EXDATE with named timezone`() {
        // 1779444000000 ms = 2026-05-22T10:00:00Z = 2026-05-22T12:00:00+02:00[Europe/Vienna]
        val from = Entity(contentValuesOf(
            JtxContract.JtxICalObject.DTSTART_TIMEZONE to "Europe/Vienna",
            JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
            JtxContract.JtxICalObject.EXDATE to "1779444000000"
        ))
        val output = VToDo()

        handler.process(from = from, main = from, to = output)

        val exdate = output.getProperty<ExDate<*>>(Property.EXDATE).getOrNull()
        assertNotNull(exdate)
        assertEquals(1, exdate?.dates?.size)
        assertEquals("20260522T120000", exdate?.value)
        assertEquals("Europe/Vienna", exdate?.getParameter<TzId>(Parameter.TZID)?.getOrNull()?.value)
    }

    @Test
    fun `Invalid RRULE is ignored without crash`() {
        val from = Entity(contentValuesOf(
            JtxContract.JtxICalObject.RRULE to "NOT_A_VALID_RRULE"
        ))
        val output = VToDo()

        handler.process(from = from, main = from, to = output)

        assertNull(output.getProperty<RRule<*>>(Property.RRULE).getOrNull())
    }

    @Test
    fun `RDATE with invalid DTSTART_TIMEZONE falls back to UTC`() {
        val from = Entity(contentValuesOf(
            JtxContract.JtxICalObject.DTSTART_TIMEZONE to "INVALID_TIMEZONE",
            JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
            JtxContract.JtxICalObject.RDATE to "1779451200000"
        ))
        val output = VToDo()

        handler.process(from = from, main = from, to = output)

        val rdate = output.getProperty<RDate<*>>(Property.RDATE).getOrNull()
        assertNotNull(rdate)
        assertEquals(1, rdate?.dates?.size)
        assertEquals("20260522T120000Z", rdate?.value)
    }

    @Test
    fun `Main ignores RECURID`() {
        val from = Entity(contentValuesOf(
            JtxContract.JtxICalObject.RECURID to "20260516T120000Z",
            JtxContract.JtxICalObject.RECURID_TIMEZONE to ZoneOffset.UTC.id,
            JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5"
        ))
        val output = VToDo()

        handler.process(from = from, main = from, to = output)

        assertNull(output.getProperty<RecurrenceId<*>>(Property.RECURRENCE_ID).getOrNull())
        assertNotNull(output.getProperty<RRule<*>>(Property.RRULE).getOrNull())
    }

    // ===== Exception tests =====

    @Test
    fun `Exception without RECURID adds no RECURRENCE-ID`() {
        val main = Entity(contentValuesOf(
            JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5"
        ))
        val exception = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = exception, main = main, to = output)

        assertNull(output.getProperty<RecurrenceId<*>>(Property.RECURRENCE_ID).getOrNull())
    }

    @Test
    fun `Exception with all-day RECURID`() {
        val main = Entity(contentValuesOf(JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5"))
        val exception = Entity(contentValuesOf(
            JtxContract.JtxICalObject.RECURID to "20260523",
            JtxContract.JtxICalObject.RECURID_TIMEZONE to TZ_ALLDAY
        ))
        val output = VToDo()

        handler.process(from = exception, main = main, to = output)

        val recurId = output.getProperty<RecurrenceId<*>>(Property.RECURRENCE_ID).getOrNull()
        assertNotNull(recurId)
        assertEquals("20260523", recurId?.value)
        assertNull(recurId?.getParameter<TzId>(Parameter.TZID)?.getOrNull())
    }

    @Test
    fun `Exception with floating RECURID`() {
        val main = Entity(contentValuesOf(JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5"))
        // null RECURID_TIMEZONE → floating
        val exception = Entity(contentValuesOf(
            JtxContract.JtxICalObject.RECURID to "20260516T120000"
        ))
        val output = VToDo()

        handler.process(from = exception, main = main, to = output)

        val recurId = output.getProperty<RecurrenceId<*>>(Property.RECURRENCE_ID).getOrNull()
        assertNotNull(recurId)
        assertEquals("20260516T120000", recurId?.value)
        assertNull(recurId?.getParameter<TzId>(Parameter.TZID)?.getOrNull())
    }

    @Test
    fun `Exception with UTC RECURID has no TZID parameter`() {
        val main = Entity(contentValuesOf(JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5"))
        val exception = Entity(contentValuesOf(
            JtxContract.JtxICalObject.RECURID to "20260516T120000Z",
            JtxContract.JtxICalObject.RECURID_TIMEZONE to ZoneOffset.UTC.id
        ))
        val output = VToDo()

        handler.process(from = exception, main = main, to = output)

        val recurId = output.getProperty<RecurrenceId<*>>(Property.RECURRENCE_ID).getOrNull()
        assertNotNull(recurId)
        assertEquals("20260516T120000Z", recurId?.value)
        assertNull(recurId?.getParameter<TzId>(Parameter.TZID)?.getOrNull())
    }

    @Test
    fun exceptionWithRecuridTimezoneUTC_hasNoTZIDParameter() {
        val main = Entity(contentValuesOf(JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5"))
        val exception = Entity(contentValuesOf(
            JtxContract.JtxICalObject.RECURID to "20260516T120000Z",
            JtxContract.JtxICalObject.RECURID_TIMEZONE to "UTC"
        ))
        val output = VToDo()

        handler.process(from = exception, main = main, to = output)

        val recurId = output.getProperty<RecurrenceId<*>>(Property.RECURRENCE_ID).getOrNull()
        assertNotNull(recurId)
        assertEquals("20260516T120000Z", recurId?.value)
        assertNull(recurId?.getParameter<TzId>(Parameter.TZID)?.getOrNull())
    }

    @Test
    fun `Exception with named timezone RECURID`() {
        val main = Entity(contentValuesOf(JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5"))
        val exception = Entity(contentValuesOf(
            JtxContract.JtxICalObject.RECURID to "20260516T120000",
            JtxContract.JtxICalObject.RECURID_TIMEZONE to "Europe/Vienna"
        ))
        val output = VToDo()

        handler.process(from = exception, main = main, to = output)

        val recurId = output.getProperty<RecurrenceId<*>>(Property.RECURRENCE_ID).getOrNull()
        assertNotNull(recurId)
        assertEquals("20260516T120000", recurId?.value)
        assertEquals("Europe/Vienna", recurId?.getParameter<TzId>(Parameter.TZID)?.getOrNull()?.value)
    }

    @Test
    fun `Exception ignores RRULE, RDATE and EXDATE`() {
        val main = Entity(contentValuesOf(JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5"))
        val exception = Entity(contentValuesOf(
            JtxContract.JtxICalObject.RECURID to "20260516T120000",
            JtxContract.JtxICalObject.RECURID_TIMEZONE to "Europe/Vienna",
            JtxContract.JtxICalObject.RRULE to "FREQ=WEEKLY;COUNT=3",
            JtxContract.JtxICalObject.RDATE to "1779451200000",
            JtxContract.JtxICalObject.EXDATE to "1779624000000"
        ))
        val output = VToDo()

        handler.process(from = exception, main = main, to = output)

        assertNull(output.getProperty<RRule<*>>(Property.RRULE).getOrNull())
        assertNull(output.getProperty<RDate<*>>(Property.RDATE).getOrNull())
        assertNull(output.getProperty<ExDate<*>>(Property.EXDATE).getOrNull())
        assertNotNull(output.getProperty<RecurrenceId<*>>(Property.RECURRENCE_ID).getOrNull())
    }
}
