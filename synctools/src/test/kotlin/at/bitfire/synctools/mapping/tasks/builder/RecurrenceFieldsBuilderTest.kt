/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.ContentValues
import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.DateList
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import org.dmfs.tasks.contract.TaskContract.Tasks
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
    fun `No recurrence fields`() {
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
    fun `RRULE is set`() {
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
    fun `RDATE all-day dates`() {
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
    fun `EXDATE all-day dates`() {
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

}
