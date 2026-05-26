/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.dateTimeValue
import at.bitfire.dateValue
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.Period
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.ExRule
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.Temporal

@RunWith(RobolectricTestRunner::class)
class RecurrenceFieldsBuilderTest {

    private val builder = RecurrenceFieldsBuilder()

    @Test
    fun `Exception event`() {
        // Exceptions (of recurring events) must never have recurrence properties themselves.
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(
                DtStart<Temporal>(),
                RRule<Temporal>("FREQ=DAILY;COUNT=1"),
                RDate<Temporal>(),
                ExDate<Temporal>()
            )),
            main = VEvent(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.RRULE to null,
            Events.RDATE to null,
            Events.EXRULE to null,
            Events.EXDATE to null
        ), result.entityValues)
    }

    @Test
    fun `EXDATE for non-recurring event`() {
        val main = VEvent(propertyListOf(
            DtStart<Temporal>(),
            ExDate<Temporal>()
        ))
        val result = Entity(ContentValues())
        builder.build(
            from = main,
            main = main,
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.RRULE to null,
            Events.RDATE to null,
            Events.EXRULE to null,
            Events.EXDATE to null
        ), result.entityValues)
    }

    @Test
    fun `Single RRULE`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(LocalDateTime.now()),
            RRule<Temporal>("FREQ=DAILY;COUNT=10")
        ))
        builder.build(
            from = event,
            main = event,
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.RRULE to "FREQ=DAILY;COUNT=10",
            Events.RDATE to null,
            Events.EXRULE to null,
            Events.EXDATE to null
        ), result.entityValues)
    }

    @Test
    fun `Multiple RRULEs`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(LocalDateTime.now()),
            RRule<Temporal>("FREQ=YEARLY;BYMONTH=4;BYDAY=-1SU"),
            RRule<Temporal>("FREQ=YEARLY;BYMONTH=10;BYDAY=1SU")
        ))
        builder.build(
            from = event,
            main = event,
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.RRULE to "FREQ=YEARLY;BYMONTH=4;BYDAY=-1SU\nFREQ=YEARLY;BYMONTH=10;BYDAY=1SU",
            Events.RDATE to null,
            Events.EXRULE to null,
            Events.EXDATE to null
        ), result.entityValues)
    }

    @Test
    fun `Single RDATE`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateValue("20250917")),
            RDate(DateList(dateValue("20250918")))
        ))
        builder.build(
            from = event,
            main = event,
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.RRULE to null,
            Events.RDATE to "20250917T000000Z,20250918T000000Z",
            Events.EXRULE to null,
            Events.EXDATE to null
        ), result.entityValues)
    }

    @Test
    fun `RDATE with infinite RRULE present`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateValue("20250917")),
            RRule<Temporal>("FREQ=DAILY"),
            RDate(DateList(dateValue("20250918")))
        ))
        builder.build(
            from = event,
            main = event,
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.RRULE to "FREQ=DAILY",
            Events.RDATE to null,
            Events.EXRULE to null,
            Events.EXDATE to null
        ), result.entityValues)
    }

    @Test
    fun `RDATE with PERIOD`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateValue("20250917")),
            RDate(listOf(
                Period(dateTimeValue("19960403T020000Z"), dateTimeValue("19960403T040000Z")),
                Period(dateTimeValue("19960404T010000Z"), Duration.ofHours(3))
            ))
        ))

        builder.build(from = event, main = event, to = result)

        assertContentValuesEqual(contentValuesOf(
            Events.RRULE to null,
            Events.RDATE to null,   // RDATE PERIOD not supported yet
            Events.EXRULE to null,
            Events.EXDATE to null
        ), result.entityValues)
    }

    @Test
    fun `Single EXRULE`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(LocalDateTime.now()),
            RRule<Temporal>("FREQ=DAILY"),
            ExRule<Temporal>(ParameterList(), "FREQ=WEEKLY")
        ))
        builder.build(
            from = event,
            main = event,
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.RRULE to "FREQ=DAILY",
            Events.RDATE to null,
            Events.EXRULE to "FREQ=WEEKLY",
            Events.EXDATE to null
        ), result.entityValues)
    }

    @Test
    fun `Single EXDATE`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateValue("20250918")),
            RRule<Temporal>("FREQ=DAILY"),
            ExDate(DateList(dateValue("20250920")))
        ))
        builder.build(
            from = event,
            main = event,
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.RRULE to "FREQ=DAILY",
            Events.RDATE to null,
            Events.EXRULE to null,
            Events.EXDATE to "20250920T000000Z"
        ), result.entityValues)
    }

}