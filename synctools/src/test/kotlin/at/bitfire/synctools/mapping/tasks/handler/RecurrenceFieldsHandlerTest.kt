/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.dateTimeValue
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.TzId
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId
import java.time.temporal.Temporal
import kotlin.jvm.optionals.getOrNull

@RunWith(RobolectricTestRunner::class)
class RecurrenceFieldsHandlerTest {

    private val tzVienna = ZoneId.of("Europe/Vienna")

    private val handler = RecurrenceFieldsHandler()


    @Test
    fun `No recurrence fields leaves task empty`() {
        val input = Entity(ContentValues())
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertNull(task.rRule)
        assertTrue(task.rDates.isEmpty())
        assertTrue(task.exDates.isEmpty())
    }

    @Test
    fun `RRULE without DTSTART is parsed without alignment`() {
        val input = Entity(contentValuesOf(Tasks.RRULE to "FREQ=DAILY;COUNT=10"))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(RRule<Temporal>("FREQ=DAILY;COUNT=10"), task.rRule)
    }

    @Test
    fun `RRULE with COUNT is parsed correctly`() {
        val input = Entity(
            contentValuesOf(
                Tasks.DTSTART to 1759403653000L,    // 2025-10-02T11:14:13Z
                Tasks.RRULE to "FREQ=WEEKLY;COUNT=5"
            )
        )
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(RRule<Temporal>("FREQ=WEEKLY;COUNT=5"), task.rRule)
    }

    @Test
    fun `RRULE with UNTIL before DTSTART is skipped`() {
        val input = Entity(
            contentValuesOf(
                Tasks.DTSTART to 1759403653000L,    // 2025-10-02T11:14:13Z
                Tasks.RRULE to "FREQ=DAILY;UNTIL=20251002T111300Z"
            )
        )
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertNull(task.rRule)
    }

    @Test
    fun `RRULE with DATE UNTIL aligned to UTC DATE-TIME when DTSTART is DATE-TIME`() {
        val input = Entity(
            contentValuesOf(
                Tasks.DTSTART to 1759403653000L,    // 2025-10-02T11:14:13Z
                Tasks.RRULE to "FREQ=DAILY;UNTIL=20251015"
            )
        )
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        // UNTIL date aligned to DTSTART's time in UTC
        assertEquals("FREQ=DAILY;UNTIL=20251015T111413Z", task.rRule!!.value)
    }

    @Test
    fun `RRULE with DATE-TIME UNTIL aligned to DATE when DTSTART is all-day`() {
        val input = Entity(
            contentValuesOf(
                Tasks.DTSTART to 1759363200000L,    // 2025-10-02T00:00:00Z (all-day)
                Tasks.IS_ALLDAY to 1,
                Tasks.RRULE to "FREQ=DAILY;UNTIL=20251015T153118Z"
            )
        )
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals("FREQ=DAILY;UNTIL=20251015", task.rRule!!.value)
    }

    @Test
    fun `RDATE is parsed into rDates`() {
        val input = Entity(contentValuesOf(Tasks.RDATE to "20251010T010203Z"))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(1, task.rDates.size)
    }

    @Test
    fun `EXDATE all-day has VALUE=DATE`() {
        val input = Entity(
            contentValuesOf(
                Tasks.IS_ALLDAY to 1,
                Tasks.EXDATE to "20251010"
            )
        )
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(1, task.exDates.size)
        assertEquals("20251010", task.exDates[0].value)
    }

    @Test
    fun `Invalid RRULE is ignored without crash`() {
        val input = Entity(contentValuesOf(Tasks.RRULE to "NOT_A_VALID_RRULE"))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertNull(task.rRule)
    }

    @Test
    fun `Invalid RDATE is ignored without crash`() {
        val input = Entity(contentValuesOf(Tasks.RDATE to "NOT_A_VALID_RDATE"))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertTrue(task.rDates.isEmpty())
    }

    @Test
    fun `Invalid EXDATE is ignored without crash`() {
        val input = Entity(contentValuesOf(Tasks.EXDATE to "NOT_A_VALID_EXDATE"))
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertTrue(task.exDates.isEmpty())
    }

    @Test
    fun `RDATE floating datetime with Tasks_TZ uses timezone`() {
        val input = Entity(
            contentValuesOf(
                Tasks.RDATE to "20251010T010203",
                Tasks.TZ to "Europe/Vienna"
            )
        )
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(1, task.rDates.size)
        assertEquals(
            RDate(
                ParameterList(listOf(TzId("Europe/Vienna"))),
                DateList(dateTimeValue("20251010T010203", tzVienna))
            ),
            task.rDates.first()
        )
    }

    @Test
    fun `EXDATE floating datetime with Tasks_TZ uses timezone`() {
        val input = Entity(
            contentValuesOf(
                Tasks.EXDATE to "20251010T010203",
                Tasks.TZ to "Europe/Vienna"
            )
        )
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(1, task.exDates.size)
        assertEquals(
            ExDate(
                ParameterList(listOf(TzId("Europe/Vienna"))),
                DateList(dateTimeValue("20251010T010203", tzVienna))
            ),
            task.exDates.first()
        )
    }

    @Test
    fun `RDATE with UTC value is not double-prefixed`() {
        val input = Entity(
            contentValuesOf(
                Tasks.RDATE to "20251010T010203Z",
                Tasks.TZ to "Europe/Vienna"
            )
        )
        val task = VToDo()

        handler.process(from = input, main = input, to = task)

        assertEquals(1, task.rDates.size)
        assertEquals(RDate(DateList(dateTimeValue("20251010T010203Z"))), task.rDates.first())
    }


    // withTzPrefix tests

    @Test
    fun `withTzPrefix adds prefix for floating datetime`() {
        assertEquals("Europe/Vienna;20251010T010203", handler.withTzPrefix("20251010T010203", "Europe/Vienna"))
    }

    @Test
    fun `withTzPrefix does not add prefix when tzId is null`() {
        assertEquals("20251010T010203Z", handler.withTzPrefix("20251010T010203Z", null))
    }

    @Test
    fun `withTzPrefix does not add prefix when tzId is UTC`() {
        assertEquals("20251010T010203Z", handler.withTzPrefix("20251010T010203Z", "UTC"))
    }

    @Test
    fun `withTzPrefix does not add prefix when prefix already present`() {
        assertEquals("Europe/Vienna;20251010T010203", handler.withTzPrefix("Europe/Vienna;20251010T010203", "Europe/Vienna"))
    }

}

private val VToDo.rRule: RRule<*>?
    get() = getProperty<RRule<*>>(Property.RRULE).getOrNull()

private val VToDo.rDates: List<RDate<Temporal>>
    get() = getProperties(Property.RDATE)

private val VToDo.exDates: List<ExDate<Temporal>>
    get() = getProperties(Property.EXDATE)
