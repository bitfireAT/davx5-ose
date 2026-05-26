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
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.RRule
import net.fortuna.ical4j.model.property.RecurrenceId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.temporal.Temporal

@RunWith(RobolectricTestRunner::class)
class OriginalInstanceTimeBuilderTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzShanghai = tzRegistry.getTimeZone("Asia/Shanghai")
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")

    private val builder = OriginalInstanceTimeBuilder()

    @Test
    fun `Main event`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(DtStart(LocalDateTime.now())))
        builder.build(
            from = event,
            main = event,
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.ORIGINAL_ALL_DAY to null,
            Events.ORIGINAL_INSTANCE_TIME to null
        ), result.entityValues)
    }

    @Test
    fun `Exception (RECURRENCE-ID is DATE, main DTSTART is DATE)`() {
        val result = Entity(ContentValues())
        builder.build(
            main = VEvent(propertyListOf(
                DtStart(dateValue("20200706")),
                RRule<Temporal>("FREQ=WEEKLY;COUNT=3")    // add RRULE to make event recurring
            )),
            from = VEvent(propertyListOf(
                RecurrenceId(dateValue("20200707")),
                DtStart(dateTimeValue("20200706T123000", tzVienna))
            )),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.ORIGINAL_ALL_DAY to 1,
            Events.ORIGINAL_INSTANCE_TIME to 1594080000000L
        ), result.entityValues)
    }

    @Test
    fun `Exception (RECURRENCE-ID is DATE, main DTSTART is DATE-TIME)`() {
        val result = Entity(ContentValues())
        builder.build(
            main = VEvent(propertyListOf(
                DtStart(dateTimeValue("20200706T193000", tzVienna)),
                RRule<Temporal>("FREQ=DAILY;COUNT=10")    // add RRULE to make event recurring
            )),
            from = VEvent(propertyListOf(
                RecurrenceId(dateValue("20200707")),  // invalid! should be rewritten to DateTime("20200707T193000", tzVienna)
                DtStart(dateTimeValue("20200706T203000", tzShanghai))
            )),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.ORIGINAL_ALL_DAY to 0,
            Events.ORIGINAL_INSTANCE_TIME to 1594143000000L
        ), result.entityValues)
    }

    @Test
    fun `Exception (RECURRENCE-ID is DATE-TIME, main DTSTART is DATE)`() {
        val result = Entity(ContentValues())
        builder.build(
            main = VEvent(propertyListOf(
                DtStart(dateValue("20200706")),
                RRule<Temporal>("FREQ=WEEKLY;COUNT=3")      // add RRULE to make event recurring
            )),
            from = VEvent(propertyListOf(
                RecurrenceId(dateTimeValue("20200707T000000", tzVienna)),   // invalid! should be rewritten to Date("20200707")
                DtStart(dateTimeValue("20200706T123000", tzVienna))
            )),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.ORIGINAL_ALL_DAY to 1,
            Events.ORIGINAL_INSTANCE_TIME to 1594080000000L
        ), result.entityValues)
    }

    @Test
    fun `Exception (RECURRENCE-ID is DATE-TIME, main DTSTART is DATE-TIME)`() {
        val result = Entity(ContentValues())
        builder.build(
            main = VEvent(propertyListOf(
                DtStart(dateTimeValue("20200706T193000", tzVienna)),
                RRule<Temporal>("FREQ=DAILY;COUNT=10")      // add RRULE to make event recurring
            )),
            from = VEvent(propertyListOf(
                RecurrenceId(dateTimeValue("20200707T193000", tzVienna)),
                DtStart(dateTimeValue("20200706T203000", tzShanghai))
            )),
            to = result
        )
        assertContentValuesEqual(contentValuesOf(
            Events.ORIGINAL_ALL_DAY to 0,
            Events.ORIGINAL_INSTANCE_TIME to 1594143000000L
        ), result.entityValues)
    }

}