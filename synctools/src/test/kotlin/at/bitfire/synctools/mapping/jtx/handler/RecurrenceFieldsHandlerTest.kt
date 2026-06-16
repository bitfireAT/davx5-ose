/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.dateTimeValue
import at.bitfire.dateValue
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import at.techbee.jtx.JtxContract
import io.mockk.mockk
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.TzId
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.transform.recurrence.Frequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.Temporal
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class RecurrenceFieldsHandlerTest {

    private val tzVienna = ZoneId.of("Europe/Vienna")

    private val handler = RecurrenceFieldsHandler()

    @Test
    fun `No recurrence fields leaves task empty`() {
        val input = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.rRule)
        assertTrue(output.rDates.isEmpty())
        assertTrue(output.exDates.isEmpty())
        assertNull(output.recurrenceId)
    }

    @Test
    fun `RRULE without DTSTART is parsed without alignment`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=10"))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(RRule<Temporal>("FREQ=DAILY;COUNT=10"), output.rRule)
    }

    @Test
    fun `RRULE with COUNT is parsed correctly`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART to timestamp("20251002T111413Z"),
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to "UTC",
                JtxContract.JtxICalObject.RRULE to "FREQ=WEEKLY;COUNT=5"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(RRule<Temporal>("FREQ=WEEKLY;COUNT=5"), output.rRule)
    }

    @Test
    fun `RRULE with UNTIL before DTSTART is skipped`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART to timestamp("20251002T111413Z"),
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to "UTC",
                JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;UNTIL=20251002T111300Z"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.rRule)
    }

    @Test
    fun `RRULE with DATE UNTIL aligned to UTC DATE-TIME when DTSTART is DATE-TIME`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART to timestamp("20251002T111413Z"),
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to "UTC",
                JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;UNTIL=20251015"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals("FREQ=DAILY;UNTIL=20251015T111413Z", output.rRule!!.value)
    }

    @Test
    fun `RRULE with DATE-TIME UNTIL aligned to DATE when DTSTART is all-day`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART to timestamp("20251002T000000Z"),
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to JtxContract.JtxICalObject.TZ_ALLDAY,
                JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;UNTIL=20251015T153118Z"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals("FREQ=DAILY;UNTIL=20251015", output.rRule!!.value)
    }

    @Test
    fun `RRULE with invalid DTSTART timezone falls back to UTC`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART to timestamp("20251002T111413Z"),
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to "Invalid/Timezone",
                JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;UNTIL=20251015"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals("FREQ=DAILY;UNTIL=20251015T111413Z", output.rRule!!.value)
    }

    @Test
    fun `RDATE is parsed into rDates`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to "UTC",
                JtxContract.JtxICalObject.RDATE to timestamp("20260522T120000Z")
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(1, output.rDates.size)
        assertEquals(
            RDate<Temporal>(ParameterList(listOf(Value.DATE_TIME)), "20260522T120000Z"),
            output.rDates.first()
        )
    }

    @Test
    fun `RDATE all-day has VALUE=DATE`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to JtxContract.JtxICalObject.TZ_ALLDAY,
                JtxContract.JtxICalObject.RDATE to timestamp("20260522T120000Z")
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(1, output.rDates.size)
        assertEquals(
            RDate<Temporal>(ParameterList(listOf(Value.DATE)), "20260522"),
            output.rDates.first()
        )
    }

    @Test
    fun `EXDATE all-day has VALUE=DATE`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to JtxContract.JtxICalObject.TZ_ALLDAY,
                JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
                JtxContract.JtxICalObject.EXDATE to timestamp("20260522T120000Z")
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(1, output.exDates.size)
        assertEquals(
            ExDate<Temporal>(ParameterList(listOf(Value.DATE)), "20260522"),
            output.exDates.first()
        )
    }

    @Test
    fun `EXDATE without RRULE or RDATE is ignored`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to "UTC",
                JtxContract.JtxICalObject.EXDATE to timestamp("20260522T120000Z")
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertTrue(output.exDates.isEmpty())
    }

    @Test
    fun `Invalid RRULE is ignored without crash`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.RRULE to "NOT_A_VALID_RRULE"))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertNull(output.rRule)
    }

    @Test
    fun `Invalid RDATE is ignored without crash`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.RDATE to "NOT_A_VALID_RDATE"))
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertTrue(output.rDates.isEmpty())
    }

    @Test
    fun `RDATE invalid timestamp entries are ignored`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to "UTC",
                JtxContract.JtxICalObject.RDATE to "invalid,${timestamp("20260522T120000Z")}"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(1, output.rDates.size)
        assertEquals(
            RDate<Temporal>(ParameterList(listOf(Value.DATE_TIME)), "20260522T120000Z"),
            output.rDates.first()
        )
    }

    @Test
    fun `Invalid EXDATE is ignored without crash`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
                JtxContract.JtxICalObject.EXDATE to "NOT_A_VALID_EXDATE"
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(RRule<Temporal>("FREQ=DAILY;COUNT=5"), output.rRule)
        assertTrue(output.exDates.isEmpty())
    }

    @Test
    fun `RDATE with named DTSTART timezone uses timezone`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to "Europe/Vienna",
                JtxContract.JtxICalObject.RDATE to timestamp("20260522T120000Z")
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(1, output.rDates.size)
        assertEquals(
            RDate<Temporal>(ParameterList(listOf(Value.DATE_TIME, TzId("Europe/Vienna"))), "20260522T140000"),
            output.rDates.first()
        )
    }

    @Test
    fun `RDATE with invalid DTSTART timezone falls back to UTC`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to "Invalid/Timezone",
                JtxContract.JtxICalObject.RDATE to timestamp("20260522T120000Z")
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(1, output.rDates.size)
        assertEquals(
            RDate<Temporal>(ParameterList(listOf(Value.DATE_TIME)), "20260522T120000Z"),
            output.rDates.first()
        )
    }

    @Test
    fun `EXDATE with named DTSTART timezone uses timezone`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to "Europe/Vienna",
                JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
                JtxContract.JtxICalObject.EXDATE to timestamp("20260522T120000Z")
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(1, output.exDates.size)
        assertEquals(
            ExDate<Temporal>(ParameterList(listOf(Value.DATE_TIME, TzId("Europe/Vienna"))), "20260522T140000"),
            output.exDates.first()
        )
    }

    @Test
    fun `EXDATE with invalid DTSTART timezone falls back to UTC`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.DTSTART_TIMEZONE to "Invalid/Timezone",
                JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
                JtxContract.JtxICalObject.EXDATE to timestamp("20260522T120000Z")
            )
        )
        val output = VToDo()

        handler.process(from = input, main = input, to = output)

        assertEquals(1, output.exDates.size)
        assertEquals(
            ExDate<Temporal>(ParameterList(listOf(Value.DATE_TIME)), "20260522T120000Z"),
            output.exDates.first()
        )
    }

    @Test
    fun `alignUntil(recurUntil=null)`() {
        val recur = Recur.Builder<Temporal>()
            .frequency(Frequency.DAILY)
            .build()
        val result = handler.alignUntil(recur, mockk())

        assertSame(recur, result)
    }

    @Test
    fun `alignUntil(recurUntil=DATE, startDate=DATE)`() {
        val recur = Recur.Builder<Temporal>()
            .frequency(Frequency.DAILY)
            .until(dateValue("20251015"))
            .build()
        val result = handler.alignUntil(recur, LocalDate.now())

        assertSame(recur, result)
    }

    @Test
    fun `alignUntil(recurUntil=DATE, startDate=DATE-TIME)`() {
        val result = handler.alignUntil(
            recur = Recur.Builder<Temporal>()
                .frequency(Frequency.DAILY)
                .until(dateValue("20251015"))
                .build(),
            startTemporal = dateTimeValue("20250101T010203", tzVienna)
        )

        assertEquals(
            Recur.Builder<Temporal>()
                .frequency(Frequency.DAILY)
                .until(dateTimeValue("20251014T230203Z"))
                .build(),
            result
        )
    }

    @Test
    fun `alignUntil(recurUntil=DATE-TIME, startDate=DATE)`() {
        val result = handler.alignUntil(
            recur = Recur.Builder<Temporal>()
                .frequency(Frequency.DAILY)
                .until(dateTimeValue("20251015T153118", tzVienna))
                .build(),
            startTemporal = LocalDate.now()
        )

        assertEquals(
            Recur.Builder<Temporal>()
                .frequency(Frequency.DAILY)
                .until(dateValue("20251015"))
                .build(),
            result
        )
    }

    @Test
    fun `alignUntil(recurUntil=DATE-TIME, startDate=DATE-TIME)`() {
        val result = handler.alignUntil(
            recur = Recur.Builder<Temporal>()
                .frequency(Frequency.DAILY)
                .until(dateTimeValue("20251015T153118", tzVienna))
                .build(),
            startTemporal = LocalDateTime.now()
        )

        assertEquals(
            Recur.Builder<Temporal>()
                .frequency(Frequency.DAILY)
                .until(dateTimeValue("20251015T133118Z"))
                .build(),
            result
        )
    }

    @Test
    fun `Exception does not map RRULE RDATE or EXDATE`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.RECURID to "20260516T120000Z",
                JtxContract.JtxICalObject.RECURID_TIMEZONE to "UTC",
                JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
                JtxContract.JtxICalObject.RDATE to timestamp("20260522T120000Z"),
                JtxContract.JtxICalObject.EXDATE to timestamp("20260522T120000Z")
            )
        )
        val main = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = main, to = output)

        assertNull(output.rRule)
        assertTrue(output.rDates.isEmpty())
        assertTrue(output.exDates.isEmpty())
        assertEquals(
            RecurrenceId<Temporal>(ParameterList(), "20260516T120000Z"),
            output.recurrenceId
        )
    }

    @Test
    fun `RECURRENCE-ID with UTC timezone is mapped`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.RECURID to "20260516T120000Z",
                JtxContract.JtxICalObject.RECURID_TIMEZONE to "UTC"
            )
        )
        val main = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = main, to = output)

        assertEquals(
            RecurrenceId<Temporal>(ParameterList(), "20260516T120000Z"),
            output.recurrenceId
        )
    }

    @Test
    fun `RECURRENCE-ID with UTC offset timezone is mapped without TZID`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.RECURID to "20260516T120000Z",
                JtxContract.JtxICalObject.RECURID_TIMEZONE to ZoneOffset.UTC.id
            )
        )
        val main = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = main, to = output)

        assertEquals(
            RecurrenceId<Temporal>(ParameterList(), "20260516T120000Z"),
            output.recurrenceId
        )
    }

    @Test
    fun `RECURRENCE-ID with named timezone is mapped`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.RECURID to "20260516T120000",
                JtxContract.JtxICalObject.RECURID_TIMEZONE to "Europe/Vienna"
            )
        )
        val main = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = main, to = output)

        assertEquals(
            RecurrenceId<Temporal>(ParameterList(listOf(TzId("Europe/Vienna"))), "20260516T120000"),
            output.recurrenceId
        )
    }

    @Test
    fun `RECURRENCE-ID with floating time is mapped`() {
        val input = Entity(contentValuesOf(JtxContract.JtxICalObject.RECURID to "20260516T120000"))
        val main = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = main, to = output)

        assertEquals(RecurrenceId<Temporal>(ParameterList(), "20260516T120000"), output.recurrenceId)
    }

    @Test
    fun `RECURRENCE-ID with all-day timezone is mapped with VALUE DATE`() {
        val input = Entity(
            contentValuesOf(
                JtxContract.JtxICalObject.RECURID to "20260523",
                JtxContract.JtxICalObject.RECURID_TIMEZONE to JtxContract.JtxICalObject.TZ_ALLDAY
            )
        )
        val main = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = main, to = output)

        assertEquals(
            RecurrenceId<Temporal>(ParameterList(listOf(Value.DATE)), "20260523"),
            output.recurrenceId
        )
    }

    @Test
    fun `Exception without RECURRENCE-ID is ignored`() {
        val input = Entity(ContentValues())
        val main = Entity(ContentValues())
        val output = VToDo()

        handler.process(from = input, main = main, to = output)

        assertNull(output.recurrenceId)
    }
}

private val VToDo.rRule: RRule<*>?
    get() = getProperty<RRule<*>>(Property.RRULE).getOrNull()

private val VToDo.rDates: List<RDate<Temporal>>
    get() = getProperties(Property.RDATE)

private val VToDo.exDates: List<ExDate<Temporal>>
    get() = getProperties(Property.EXDATE)

private val VToDo.recurrenceId: RecurrenceId<*>?
    get() = getProperty<RecurrenceId<*>>(Property.RECURRENCE_ID).getOrNull()

private fun timestamp(value: String): String =
    dateTimeValue(value).toTimestamp().toString()
