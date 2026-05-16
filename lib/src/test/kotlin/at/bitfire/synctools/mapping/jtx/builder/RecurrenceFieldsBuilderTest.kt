/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.dateTimeValue
import at.bitfire.dateValue
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.test.assertContentValuesEqual
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.Period
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneOffset
import java.time.temporal.Temporal

@RunWith(RobolectricTestRunner::class)
class RecurrenceFieldsBuilderTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")

    private val builder = RecurrenceFieldsBuilder()

    @Test
    fun `non-recurring task`() {
        val task = VToDo()
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertContentValuesEqual(
            expected = contentValuesOf(
                JtxContract.JtxICalObject.RECURID to null,
                JtxContract.JtxICalObject.RECURID_TIMEZONE to null,
                JtxContract.JtxICalObject.RRULE to null,
                JtxContract.JtxICalObject.RDATE to null,
                JtxContract.JtxICalObject.EXDATE to null,
            ),
            actual = output.entityValues
        )
    }

    @Test
    fun `recurring task`() {
        val task = VToDo().apply {
            this += DtStart(dateTimeValue("20260515T120000Z"))
            this += RRule<Temporal>("FREQ=DAILY;COUNT=5")
        }
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertContentValuesEqual(
            expected = contentValuesOf(
                JtxContract.JtxICalObject.RECURID to null,
                JtxContract.JtxICalObject.RECURID_TIMEZONE to null,
                JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
                JtxContract.JtxICalObject.RDATE to null,
                JtxContract.JtxICalObject.EXDATE to null,
            ),
            actual = output.entityValues
        )
    }

    @Test
    fun `recurring task with RDATE with DATE-TIME value`() {
        val task = VToDo().apply {
            this += DtStart(dateTimeValue("20260515T120000Z"))
            this += RRule<Temporal>("FREQ=DAILY;COUNT=5")
            this += RDate(DateList(dateTimeValue("20260522T120000Z")))
        }
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertContentValuesEqual(
            expected = contentValuesOf(
                JtxContract.JtxICalObject.RECURID to null,
                JtxContract.JtxICalObject.RECURID_TIMEZONE to null,
                JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
                JtxContract.JtxICalObject.RDATE to "1779451200000",
                JtxContract.JtxICalObject.EXDATE to null,
            ),
            actual = output.entityValues
        )
    }

    @Test
    fun `recurring task with RDATE with PERIOD value`() {
        val task = VToDo().apply {
            this += DtStart(dateTimeValue("20260515T120000Z"))
            this += RRule<Temporal>("FREQ=DAILY;COUNT=5")
            this += RDate(listOf(Period.parse("20260522T120000Z/PT2H")))
        }
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertContentValuesEqual(
            expected = contentValuesOf(
                JtxContract.JtxICalObject.RECURID to null,
                JtxContract.JtxICalObject.RECURID_TIMEZONE to null,
                JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
                JtxContract.JtxICalObject.RDATE to null,
                JtxContract.JtxICalObject.EXDATE to null,
            ),
            actual = output.entityValues
        )
    }

    @Test
    fun `recurring task with multiple RDATE (with DATE-TIME value) properties`() {
        val task = VToDo().apply {
            this += DtStart(dateTimeValue("20260515T120000Z"))
            this += RRule<Temporal>("FREQ=DAILY;COUNT=5")
            this += RDate(DateList(dateTimeValue("20260522T120000Z")))
            this += RDate(DateList(
                dateTimeValue("20260524T120000Z"),
                dateTimeValue("20260525T120000Z")
            ))
        }
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertContentValuesEqual(
            expected = contentValuesOf(
                JtxContract.JtxICalObject.RECURID to null,
                JtxContract.JtxICalObject.RECURID_TIMEZONE to null,
                JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
                JtxContract.JtxICalObject.RDATE to "1779451200000,1779624000000,1779710400000",
                JtxContract.JtxICalObject.EXDATE to null,
            ),
            actual = output.entityValues
        )
    }

    @Test
    fun `recurring task with EXDATE`() {
        val task = VToDo().apply {
            this += DtStart(dateTimeValue("20260515T120000Z"))
            this += RRule<Temporal>("FREQ=DAILY;COUNT=5")
            this += ExDate(DateList(dateTimeValue("20260522T120000Z")))
        }
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertContentValuesEqual(
            expected = contentValuesOf(
                JtxContract.JtxICalObject.RECURID to null,
                JtxContract.JtxICalObject.RECURID_TIMEZONE to null,
                JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
                JtxContract.JtxICalObject.RDATE to null,
                JtxContract.JtxICalObject.EXDATE to "1779451200000",
            ),
            actual = output.entityValues
        )
    }

    @Test
    fun `recurring task with EXDATE with empty value`() {
        val task = VToDo().apply {
            this += DtStart(dateTimeValue("20260515T120000Z"))
            this += RRule<Temporal>("FREQ=DAILY;COUNT=5")
            this += ExDate<Temporal>("")
        }
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertContentValuesEqual(
            expected = contentValuesOf(
                JtxContract.JtxICalObject.RECURID to null,
                JtxContract.JtxICalObject.RECURID_TIMEZONE to null,
                JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
                JtxContract.JtxICalObject.RDATE to null,
                JtxContract.JtxICalObject.EXDATE to null,
            ),
            actual = output.entityValues
        )
    }

    @Test
    fun `recurring task with multiple EXDATE properties`() {
        val task = VToDo().apply {
            this += DtStart(dateTimeValue("20260515T120000Z"))
            this += RRule<Temporal>("FREQ=DAILY;COUNT=5")
            this += ExDate(DateList(dateTimeValue("20260522T120000Z")))
            this += ExDate(DateList(
                dateTimeValue("20260524T120000Z"),
                dateTimeValue("20260525T120000Z")
            ))
        }
        val output = Entity(ContentValues())

        builder.build(from = task, main = task, to = output)

        assertContentValuesEqual(
            expected = contentValuesOf(
                JtxContract.JtxICalObject.RECURID to null,
                JtxContract.JtxICalObject.RECURID_TIMEZONE to null,
                JtxContract.JtxICalObject.RRULE to "FREQ=DAILY;COUNT=5",
                JtxContract.JtxICalObject.RDATE to null,
                JtxContract.JtxICalObject.EXDATE to "1779451200000,1779624000000,1779710400000",
            ),
            actual = output.entityValues
        )
    }

    @Test
    fun `exception with RECURRENCE-ID being ZonedDateTime`() {
        val task = VToDo().apply {
            this += DtStart(dateTimeValue("20260515T120000", tzVienna))
            this += RRule<Temporal>("FREQ=DAILY;COUNT=5")
        }
        val exception = VToDo().apply {
            this += DtStart(dateTimeValue("20260516T140000", tzVienna))
            this += RecurrenceId(dateTimeValue("20260516T120000", tzVienna))
        }
        val output = Entity(ContentValues())

        builder.build(from = exception, main = task, to = output)

        assertContentValuesEqual(
            expected = contentValuesOf(
                JtxContract.JtxICalObject.RECURID to "20260516T120000",
                JtxContract.JtxICalObject.RECURID_TIMEZONE to "Europe/Vienna",
                JtxContract.JtxICalObject.RRULE to null,
                JtxContract.JtxICalObject.RDATE to null,
                JtxContract.JtxICalObject.EXDATE to null,
            ),
            actual = output.entityValues
        )
    }

    @Test
    fun `exception with RECURRENCE-ID being Instant`() {
        val task = VToDo().apply {
            this += DtStart(dateTimeValue("20260515T120000Z"))
            this += RRule<Temporal>("FREQ=DAILY;COUNT=5")
        }
        val exception = VToDo().apply {
            this += DtStart(dateTimeValue("20260516T140000Z"))
            this += RecurrenceId(dateTimeValue("20260516T120000Z"))
        }
        val output = Entity(ContentValues())

        builder.build(from = exception, main = task, to = output)

        assertContentValuesEqual(
            expected = contentValuesOf(
                JtxContract.JtxICalObject.RECURID to "20260516T120000Z",
                JtxContract.JtxICalObject.RECURID_TIMEZONE to ZoneOffset.UTC.id,
                JtxContract.JtxICalObject.RRULE to null,
                JtxContract.JtxICalObject.RDATE to null,
                JtxContract.JtxICalObject.EXDATE to null,
            ),
            actual = output.entityValues
        )
    }

    @Test
    fun `exception with RECURRENCE-ID being LocalDateTime`() {
        val task = VToDo().apply {
            this += DtStart(dateTimeValue("20260515T120000"))
            this += RRule<Temporal>("FREQ=DAILY;COUNT=5")
        }
        val exception = VToDo().apply {
            this += DtStart(dateTimeValue("20260516T140000Z"))
            this += RecurrenceId(dateTimeValue("20260516T120000"))
        }
        val output = Entity(ContentValues())

        builder.build(from = exception, main = task, to = output)

        assertContentValuesEqual(
            expected = contentValuesOf(
                JtxContract.JtxICalObject.RECURID to "20260516T120000",
                JtxContract.JtxICalObject.RECURID_TIMEZONE to null,
                JtxContract.JtxICalObject.RRULE to null,
                JtxContract.JtxICalObject.RDATE to null,
                JtxContract.JtxICalObject.EXDATE to null,
            ),
            actual = output.entityValues
        )
    }

    @Test
    fun `exception with RECURRENCE-ID being LocalDate`() {
        val task = VToDo().apply {
            this += DtStart(dateValue("20260515"))
            this += RRule<Temporal>("FREQ=DAILY;COUNT=5")
        }
        val exception = VToDo().apply {
            this += DtStart(dateValue("20260516"))
            this += RecurrenceId(dateValue("20260523"))
        }
        val output = Entity(ContentValues())

        builder.build(from = exception, main = task, to = output)

        assertContentValuesEqual(
            expected = contentValuesOf(
                JtxContract.JtxICalObject.RECURID to "20260523",
                JtxContract.JtxICalObject.RECURID_TIMEZONE to JtxContract.JtxICalObject.TZ_ALLDAY,
                JtxContract.JtxICalObject.RRULE to null,
                JtxContract.JtxICalObject.RDATE to null,
                JtxContract.JtxICalObject.EXDATE to null,
            ),
            actual = output.entityValues
        )
    }
}
