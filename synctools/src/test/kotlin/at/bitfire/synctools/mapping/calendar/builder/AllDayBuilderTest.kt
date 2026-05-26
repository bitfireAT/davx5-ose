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
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.synctools.test.assertContentValuesEqual
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtStart
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
class AllDayBuilderTest {

    private val builder = AllDayBuilder()

    @Test
    fun `No DTSTART`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(),
            main = VEvent(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.ALL_DAY to 0
        ), result.entityValues)
    }

    @Test
    fun `DTSTART is DATE`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(DtStart(LocalDate.now()))),
            main = VEvent(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.ALL_DAY to 1
        ), result.entityValues)
    }

    @Test
    fun `DTSTART is DATE-TIME`() {
        val result = Entity(ContentValues())
        builder.build(
            from = VEvent(propertyListOf(DtStart(LocalDateTime.now()))),
            main = VEvent(),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.ALL_DAY to 0
        ), result.entityValues)
    }

}