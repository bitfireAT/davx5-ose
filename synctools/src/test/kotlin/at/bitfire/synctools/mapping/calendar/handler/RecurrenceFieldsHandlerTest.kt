/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.dateTimeValue
import at.bitfire.dateValue
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Value
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.ExRule
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.transform.recurrence.Frequency
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.Temporal
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class RecurrenceFieldsHandlerTest {

    private val tzVienna = ZoneId.of("Europe/Vienna")

    private val handler = RecurrenceFieldsHandler()


    @Test
    fun `Recurring exception`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to System.currentTimeMillis(),
            Events.RRULE to "FREQ=DAILY;COUNT=10",
            Events.RDATE to "20251010T010203Z",
            Events.EXRULE to "FREQ=WEEKLY;COUNT=1",
            Events.EXDATE to "20260201T010203Z"
        ))
        handler.process(entity, Entity(ContentValues()), result)
        // exceptions must never have recurrence properties
        assertNull(result.getProperty<RRule<*>>(Property.RRULE).getOrNull())
        assertNull(result.getProperty<RDate<*>>(Property.RDATE).getOrNull())
        assertNull(result.getProperty<ExRule<*>>(Property.EXRULE).getOrNull())
        assertNull(result.getProperty<ExDate<*>>(Property.EXDATE).getOrNull())
    }

    @Test
    fun `Non-recurring main event`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to System.currentTimeMillis(),
            Events.EXRULE to "FREQ=WEEKLY;COUNT=1",
            Events.EXDATE to "20260201T010203Z"
        ))
        handler.process(entity, entity, result)
        // non-recurring events must never have recurrence properties
        assertNull(result.getProperty<RRule<*>>(Property.RRULE).getOrNull())
        assertNull(result.getProperty<RDate<*>>(Property.RDATE).getOrNull())
        assertNull(result.getProperty<ExRule<*>>(Property.EXRULE).getOrNull())
        assertNull(result.getProperty<ExDate<*>>(Property.EXDATE).getOrNull())
    }

    @Test
    fun `Recurring main event`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to 1759403653000,    // Thu Oct 02 2025 11:14:13 GMT+0000
            Events.EVENT_TIMEZONE to "UTC",
            Events.RRULE to "FREQ=DAILY;COUNT=10",      // Oct 02 ... Oct 12
            Events.RDATE to "20251002T111413Z,20251015T010203Z",    // RDATE at event start as required by Android plus Oct 15
            Events.EXRULE to "FREQ=WEEKLY;COUNT=1",     // meaningless EXRULE/EXDATE
            Events.EXDATE to "20260201T010203Z"
        ))
        handler.process(entity, entity, result)
        assertEquals(
            listOf(RRule<Temporal>("FREQ=DAILY;COUNT=10")),
            result.getProperties<RRule<Temporal>>(Property.RRULE)
        )
        assertEquals(
            listOf(RDate<Temporal>(ParameterList(), "20251015T010203Z")),
            result.getProperties<RDate<Temporal>>(Property.RDATE)
        )
        assertEquals(
            "FREQ=WEEKLY;COUNT=1",
            result.getProperties<ExRule<Temporal>>(Property.EXRULE).joinToString { it.value }
        )
        assertEquals(
            "20260201T010203Z",
            result.getProperties<ExDate<Temporal>>(Property.EXDATE).joinToString { it.value }
        )
    }

    @Test
    fun `Recurring main event (all-day)`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.ALL_DAY to 1,
            Events.DTSTART to 1759363200000,    // Thu Oct 02 2025 00:00:00 GMT+0000
            Events.RRULE to "FREQ=DAILY;COUNT=10",      // Oct 02 ... Oct 12
            Events.RDATE to "20251002,20251015",        // RDATE at event start as required by Android plus Oct 15
            Events.EXRULE to "FREQ=WEEKLY;COUNT=1",     // meaningless EXRULE/EXDATE
            Events.EXDATE to "20260201T010203Z"
        ))
        handler.process(entity, entity, result)
        assertEquals(
            listOf(RRule<Temporal>("FREQ=DAILY;COUNT=10")),
            result.getProperties<RRule<Temporal>>(Property.RRULE)
        )
        assertEquals(
            listOf(RDate(ParameterList(listOf(Value.DATE)), DateList(listOf(dateValue("20251015"))))),
            result.getProperties<RDate<LocalDate>>(Property.RDATE)
        )
        assertEquals(
            "FREQ=WEEKLY;COUNT=1",
            result.getProperties<ExRule<Temporal>>(Property.EXRULE).joinToString { it.value }
        )
        // All-day EXDATE must have VALUE=DATE
        val exDates = result.getProperties<ExDate<Temporal>>(Property.EXDATE)
        assertEquals(Value.DATE, exDates.first().getParameter<Value>(Parameter.VALUE).get())
        assertEquals("20260201", exDates.joinToString { it.value })
    }

    @Test
    fun `RRULE with UNTIL before DTSTART`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to 1759403653000,    // Thu Oct 02 2025 11:14:13 GMT+0000
            Events.RRULE to "FREQ=DAILY;UNTIL=20251002T111300Z",
            Events.EXDATE to "1759403653000"            // should be removed because the only RRULE is invalid and discarded,
                                                        // so the whole event isn't recurring anymore
        ))
        handler.process(entity, entity, result)
        assertNull(result.getProperty<RRule<*>>(Property.RRULE).getOrNull())
        assertNull(result.getProperty<ExDate<*>>(Property.EXDATE).getOrNull())
    }

    @Test
    fun `EXRULE with UNTIL before DTSTART`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to 1759403653000,    // Thu Oct 02 2025 11:14:13 GMT+0000,
            Events.RRULE to "FREQ=DAILY;COUNT=10",      // EXRULE is only processed for recurring events
            Events.EXRULE to "FREQ=DAILY;UNTIL=20251002T111300Z"
        ))
        handler.process(entity, entity, result)
        assertNull(result.getProperty<ExRule<*>>(Property.EXRULE).getOrNull())
    }

    @Test
    fun `EXDATE with explicit UTC timezone`() {
        val result = VEvent()
        val entity = Entity(contentValuesOf(
            Events.DTSTART to 1759403653000,    // Thu Oct 02 2025 11:14:13 GMT+0000,
            Events.RRULE to "FREQ=DAILY;COUNT=10",
            Events.EXDATE to "UTC;20251003T111413"
        ))

        handler.process(entity, entity, result)

        assertEquals(
            ExDate<Temporal>("20251003T111413Z"),
            result.getRequiredProperty<ExDate<*>>(Property.EXDATE)
        )
    }


    @Test
    fun `alignUntil(recurUntil=null)`() {
        val recur = Recur.Builder<Temporal>()
            .frequency(Frequency.DAILY)
            .build()
        val result = handler.alignUntil(
            recur = recur,
            startTemporal = mockk()
        )
        assertSame(recur, result)
    }

    @Test
    fun `alignUntil(recurUntil=DATE, startDate=DATE)`() {
        val recur = Recur.Builder<Temporal>()
            .frequency(Frequency.DAILY)
            .until(dateValue("20251015"))
            .build()
        val result = handler.alignUntil(
            recur = recur,
            startTemporal = LocalDate.now()
        )
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

}