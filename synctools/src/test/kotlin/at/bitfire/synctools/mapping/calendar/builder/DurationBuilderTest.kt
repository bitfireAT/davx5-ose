/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.dateTimeValue
import at.bitfire.dateValue
import at.bitfire.synctools.icalendar.propertyListOf
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.RRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.temporal.Temporal

@RunWith(RobolectricTestRunner::class)
class DurationBuilderTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")

    private val builder = DurationBuilder()

    @Test
    fun `Not a main event`() {
        val result = Entity(ContentValues())
        builder.build(VEvent(propertyListOf(
            DtStart(dateValue("20251010")),
            DtEnd(dateValue("20251011")),
            RRule<Temporal>("FREQ=DAILY;COUNT=5")
        )), VEvent(), result)
        assertTrue(result.entityValues.containsKey(Events.DURATION))
        assertNull(result.entityValues.get(Events.DURATION))
    }

    @Test
    fun `Not a recurring event`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateValue("20251010")),
            Duration(Period.ofDays(2))
        ))
        builder.build(event, event, result)
        assertTrue(result.entityValues.containsKey(Events.DURATION))
        assertNull(result.entityValues.get(Events.DURATION))
    }


    @Test
    fun `Recurring all-day event (with DURATION)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateValue("20251010")),
            Duration(Period.ofDays(3)),
            RRule<Temporal>("FREQ=DAILY;COUNT=5")
        ))
        builder.build(event, event, result)
        assertEquals("P3D", result.entityValues.get(Events.DURATION))
    }

    @Test
    fun `Recurring all-day event (with negative DURATION)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateValue("20251010")),
            Duration(Period.ofDays(-3)),    // invalid negative DURATION will be treated as positive
            RRule<Temporal>("FREQ=DAILY;COUNT=5")
        ))
        builder.build(event, event, result)
        assertEquals("P3D", result.entityValues.get(Events.DURATION))
    }

    @Test
    fun `Recurring all-day event (with zero seconds DURATION)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateValue("20251010")),
            Duration(java.time.Duration.ofSeconds(0)),
            RRule<Temporal>("FREQ=DAILY;COUNT=5")
        ))
        builder.build(event, event, result)
        assertEquals("P0D", result.entityValues.get(Events.DURATION))
    }

    @Test
    fun `Recurring non-all-day event (with DURATION)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateTimeValue("20251010T010203", tzVienna)),
            Duration(java.time.Duration.ofMinutes(90)),
            RRule<Temporal>("FREQ=DAILY;COUNT=5")
        ))
        builder.build(event, event, result)
        assertEquals("PT1H30M", result.entityValues.get(Events.DURATION))
    }

    @Test
    fun `Recurring non-all-day event (with negative DURATION)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateTimeValue("20251010T010203", tzVienna)),
            Duration(java.time.Duration.ofMinutes(-90)),    // invalid negative DURATION will be treated as positive
            RRule<Temporal>("FREQ=DAILY;COUNT=5")
        ))
        builder.build(event, event, result)
        assertEquals("PT1H30M", result.entityValues.get(Events.DURATION))
    }

    @Test
    fun `Recurring all-day event (with DTEND)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateValue("20251010")),
            DtEnd(dateValue("20251017")),
            RRule<Temporal>("FREQ=DAILY;COUNT=5")
        ))
        builder.build(event, event, result)
        assertEquals("P1W", result.entityValues.get(Events.DURATION))
    }

    @Test
    fun `Recurring all-day event (with DTEND before START)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateValue("20251017")),
            DtEnd(dateValue("20251010")),    // DTEND before DTSTART should be ignored
            RRule<Temporal>("FREQ=DAILY;COUNT=5")
        ))
        builder.build(event, event, result)
        // default duration for all-day events: one day
        assertEquals("P1D", result.entityValues.get(Events.DURATION))
    }

    @Test
    fun `Recurring all-day event (with DTEND equals START)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateValue("20251017")),
            DtEnd(dateValue("20251017")),    // DTEND equals DTSTART should be ignored
            RRule<Temporal>("FREQ=DAILY;COUNT=5")
        ))
        builder.build(event, event, result)
        // default duration for all-day events: one day
        assertEquals("P1D", result.entityValues.get(Events.DURATION))
    }

    @Test
    fun `Recurring non-all-day event (with DTEND)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateTimeValue("20251010T010203", tzVienna)),
            DtEnd(dateTimeValue("20251011T020304", tzVienna)),
            RRule<Temporal>("FREQ=DAILY;COUNT=5")
        ))
        builder.build(event, event, result)
        assertEquals("P1DT1H1M1S", result.entityValues.get(Events.DURATION))
    }

    @Test
    fun `Recurring non-all-day event (with DTEND before DTSTART)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateTimeValue("20251010T010203", tzVienna)),
            DtEnd(dateTimeValue("20251010T000203", tzVienna)),   // DTEND before DTSTART should be ignored
            RRule<Temporal>("FREQ=DAILY;COUNT=5")
        ))
        builder.build(event, event, result)
        // default duration for non-all-day events: zero seconds
        assertEquals("PT0S", result.entityValues.get(Events.DURATION))
    }

    @Test
    fun `Recurring non-all-day event (with DTEND equals DTSTART)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateTimeValue("20251010T010203", tzVienna)),
            DtEnd(dateTimeValue("20251010T010203", tzVienna)),   // DTEND equals DTSTART should be ignored
            RRule<Temporal>("FREQ=DAILY;COUNT=5")
        ))
        builder.build(event, event, result)
        // default duration for non-all-day events: zero seconds
        assertEquals("PT0S", result.entityValues.get(Events.DURATION))
    }

    @Test
    fun `Recurring all-day event (neither DURATION nor DTEND)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateValue("20251010")),
            RRule<Temporal>("FREQ=DAILY;COUNT=5"))
        )
        builder.build(event, event, result)
        assertEquals("P1D", result.entityValues.get(Events.DURATION))
    }

    @Test
    fun `Recurring non-all-day event (neither DURATION nor DTEND)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateTimeValue("20251010T010203", tzVienna)),
            RRule<Temporal>("FREQ=DAILY;COUNT=5")
        ))
        builder.build(event, event, result)
        assertEquals("PT0S", result.entityValues.get(Events.DURATION))
    }


    // alignWithDtStart

    @Test
    fun `alignWithDtStart (DTSTART all-day, DURATION all-day)`() {
        assertEquals(
            Period.ofDays(1),       // may not be 24 hours (for instance on DST switch)
            builder.alignWithDtStart(Period.ofDays(1), LocalDate.now())
        )
    }

    @Test
    fun `alignWithDtStart (DTSTART non-all-day, DURATION all-day)`() {
        assertEquals(
            java.time.Duration.ofDays(1),   // exactly 24 hours
            builder.alignWithDtStart(Period.ofDays(1), LocalDateTime.now())
        )
    }

    @Test
    fun `alignWithDtStart (DTSTART all-day, DURATION non-all-day)`() {
        assertEquals(
            Period.ofDays(1),       // may not be 24 hours (for instance on DST switch)
            builder.alignWithDtStart(java.time.Duration.ofHours(25), LocalDate.now())
        )
    }

    @Test
    fun `alignWithDtStart (DTSTART non-all-day, DURATION non-all-day)`() {
        assertEquals(
            java.time.Duration.ofDays(1),   // exactly 24 hours
            builder.alignWithDtStart(java.time.Duration.ofHours(24), LocalDateTime.now())
        )
    }


    // calculateFromDtEnd

    @Test
    fun `calculateFromDtEnd (dtStart=DATE, DtEnd=DATE)`() {
        val result = builder.calculateFromDtEnd(
            dateValue("20240328"),
            dateValue("20240330")
        )
        assertEquals(
            Period.ofDays(2),
            result
        )
    }

    @Test
    fun `calculateFromDtEnd (dtStart=DATE, DtEnd before dtStart)`() {
        val result = builder.calculateFromDtEnd(
            dateValue("20240328"),
            dateValue("20240327")
        )
        assertNull(result)
    }

    @Test
    fun `calculateFromDtEnd (dtStart=DATE, DtEnd=DATE-TIME)`() {
        val result = builder.calculateFromDtEnd(
            dateValue("20240328"),
            dateTimeValue("20240330T123412", tzVienna)
        )
        assertEquals(
            Period.ofDays(2),
            result
        )
    }

    @Test
    fun `calculateFromDtEnd (dtStart=DATE-TIME, DtEnd before dtStart)`() {
        val result = builder.calculateFromDtEnd(
            dateTimeValue("20240328T010203", tzVienna),
            dateTimeValue("20240328T000000", tzVienna)
        )
        assertNull(result)
    }

    @Test
    fun `calculateFromDtEnd (dtStart=DATE-TIME, DtEnd=DATE)`() {
        val result = builder.calculateFromDtEnd(
            dateTimeValue("20240328T010203", tzVienna),
            dateValue("20240330")
        )
        assertEquals(
            Period.ofDays(2),
            result
        )
    }

    @Test
    fun `calculateFromDtEnd (dtStart=DATE-TIME, DtEnd=DATE-TIME)`() {
        val result = builder.calculateFromDtEnd(
            dateTimeValue("20240728T010203", tzVienna),
            dateTimeValue("20240728T010203Z")     // GMT+1 with DST → 2 hours difference
        )
        assertEquals(
            java.time.Duration.ofHours(2),
            result
        )
    }

}