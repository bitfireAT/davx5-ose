/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.mapping.tasks.VToDoUtil
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import org.dmfs.tasks.contract.TaskContract.Tasks
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.Temporal

@RunWith(RobolectricTestRunner::class)
class RecurrenceFieldsBuilderTest {

    private val builder = RecurrenceFieldsBuilder()

    @Test
    fun `old No recurrence fields`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.RRULE to null,
            Tasks.RDATE to null,
            Tasks.EXDATE to null
        ), result.entityValues)
    }

    @Test
    fun `old RRULE is set`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task().also {
                it.rRule = RRule<Temporal>("FREQ=DAILY;COUNT=10")
            },
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Tasks.RRULE to "FREQ=DAILY;COUNT=10",
            Tasks.RDATE to null,
            Tasks.EXDATE to null
        ), result.entityValues)
    }

    @Test
    fun `old RDATE all-day dates`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task().also {
                it.rDates += RDate(DateList(LocalDate.of(2025, 1, 15)))
            },
            to = result
        )
        // All-day task: rDates formatted without timezone
        val rdate = result.entityValues.getAsString(Tasks.RDATE)
        assert(rdate != null) { "RDATE should be set" }
    }

    @Test
    fun `old EXDATE all-day dates`() {
        val result = Entity(ContentValues())
        builder.build(
            from = Task().also {
                it.rRule = RRule<Temporal>("FREQ=DAILY;COUNT=10")
                it.exDates += ExDate(DateList(LocalDate.of(2025, 1, 15)))
            },
            to = result
        )
        val exdate = result.entityValues.getAsString(Tasks.EXDATE)
        assert(exdate != null) { "EXDATE should be set" }
    }

    @Test
    fun `No recurrence fields`() {
        val vtodo = VToDoUtil.build()
        val result = Entity(ContentValues())
        builder.build(from = vtodo, main = vtodo, to = result)
        assertContentValuesEqual(contentValuesOf(
            Tasks.RRULE to null,
            Tasks.RDATE to null,
            Tasks.EXDATE to null
        ), result.entityValues)
    }

    @Test
    fun `RRULE is set`() {
        val vtodo = VToDoUtil.build(
            DtStart(LocalDate.of(2025, 1, 15)),
            RRule<Temporal>("FREQ=DAILY;COUNT=10")
        )
        val result = Entity(ContentValues())
        builder.build(from = vtodo, main = vtodo, to = result)
        assertContentValuesEqual(contentValuesOf(
            Tasks.RRULE to "FREQ=DAILY;COUNT=10",
            Tasks.RDATE to null,
            Tasks.EXDATE to null
        ), result.entityValues)
    }

    @Test
    fun `RDATE all-day dates`() {
        val vtodo = VToDoUtil.build(
            DtStart(LocalDate.of(2025, 1, 10)),
            RDate(DateList(LocalDate.of(2025, 1, 15)))
        )
        val result = Entity(ContentValues())
        builder.build(from = vtodo, main = vtodo, to = result)
        val rdate = result.entityValues.getAsString(Tasks.RDATE)
        assertNotNull("RDATE should be set", rdate)
    }

    @Test
    fun `EXDATE all-day dates`() {
        val vtodo = VToDoUtil.build(
            DtStart(LocalDate.of(2025, 1, 10)),
            RRule<Temporal>("FREQ=DAILY;COUNT=10"),
            ExDate(DateList(LocalDate.of(2025, 1, 15)))
        )
        val result = Entity(ContentValues())
        builder.build(from = vtodo, main = vtodo, to = result)
        val exdate = result.entityValues.getAsString(Tasks.EXDATE)
        assertNotNull("EXDATE should be set", exdate)
    }

    @Test
    fun `RRULE with DATE-TIME DTSTART`() {
        val ts = ZonedDateTime.of(2025, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC)
        val vtodo = VToDoUtil.build(
            DtStart(ts),
            RRule<Temporal>("FREQ=DAILY;COUNT=5")
        )
        val result = Entity(ContentValues())
        builder.build(from = vtodo, main = vtodo, to = result)
        assertContentValuesEqual(contentValuesOf(
            Tasks.RRULE to "FREQ=DAILY;COUNT=5",
            Tasks.RDATE to null,
            Tasks.EXDATE to null
        ), result.entityValues)
    }

    @Test
    fun `Exception VToDo - recurrence fields are nulled out`() {
        val main = VToDoUtil.build(
            DtStart(LocalDate.of(2025, 1, 15)),
            RRule<Temporal>("FREQ=DAILY;COUNT=10")
        )
        val exception = VToDoUtil.build(
            DtStart(LocalDate.of(2025, 1, 17)),
            RRule<Temporal>("FREQ=DAILY;COUNT=5")
        )
        val result = Entity(ContentValues())
        builder.build(from = exception, main = main, to = result)
        assertContentValuesEqual(contentValuesOf(
            Tasks.RRULE to null,
            Tasks.RDATE to null,
            Tasks.EXDATE to null
        ), result.entityValues)
    }

}
