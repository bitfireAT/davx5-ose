/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.DefaultTimezoneRule
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Period
import java.time.temporal.Temporal

@RunWith(RobolectricTestRunner::class)
class EndTimeBuilderTest {

    @get:Rule
    val tzRule = DefaultTimezoneRule("Europe/Berlin")

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")

    private val builder = EndTimeBuilder()

    @Test
    fun `Recurring event`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateValue("20251010")),
            DtEnd(dateValue("20251011")),
            RRule<Temporal>("FREQ=DAILY;COUNT=5")
        ))
        builder.build(event, event, result)
        assertTrue(result.entityValues.containsKey(Events.DTEND))
        assertNull(result.entityValues.get(Events.DTEND))
    }


    @Test
    fun `Non-recurring all-day event (with DTEND)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateValue("20251010")),
            DtEnd(dateValue("20251011"))
        ))
        builder.build(event, event, result)
        assertEquals(1760140800000, result.entityValues.get(Events.DTEND))
        assertEquals("UTC", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring all-day event (with DTEND before DTSTART)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateValue("20251010")),
            DtEnd(dateValue("20251001"))     // before DTSTART, should be ignored
        ))
        builder.build(event, event, result)
        // default duration: one day → 20251011
        assertEquals(1760140800000, result.entityValues.get(Events.DTEND))
        assertEquals("UTC", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring all-day event (with DTEND equals DTSTART)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateValue("20251010")),
            DtEnd(dateValue("20251010"))     // equals DTSTART, should be ignored
        ))
        builder.build(event, event, result)
        // default duration: one day → 20251011
        assertEquals(1760140800000, result.entityValues.get(Events.DTEND))
        assertEquals("UTC", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring non-all-day event (with floating DTEND)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateTimeValue("20251010T010203", tzVienna)),
            DtEnd(dateTimeValue("20251011T040506"))
        ))
        builder.build(event, event, result)
        assertEquals(1760148306000L, result.entityValues.get(Events.DTEND))
        assertEquals(tzRule.defaultZoneId.id, result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring non-all-day event (with UTC DTEND)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateTimeValue("20251010T010203", tzVienna)),
            DtEnd(dateTimeValue("20251011T040506Z"))
        ))
        builder.build(event, event, result)
        assertEquals(1760155506000, result.entityValues.get(Events.DTEND))
        assertEquals("UTC", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring non-all-day event (with zoned DTEND)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateTimeValue("20251010T010203", tzVienna)),
            DtEnd(dateTimeValue("20251011T040506", tzVienna))
        ))
        builder.build(event, event, result)
        assertEquals(1760148306000, result.entityValues.get(Events.DTEND))
        assertEquals("Europe/Vienna", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring non-all-day event (with zoned DTEND before DTSTART)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateTimeValue("20251011T040506", tzVienna)),
            DtEnd(dateTimeValue("20251010T040506", tzVienna))    // before DTSTART, should be ignored
        ))
        builder.build(event, event, result)
        // default duration: 0 sec -> DTEND == DTSTART in calendar provider
        assertEquals(1760148306000, result.entityValues.get(Events.DTEND))
        assertEquals("Europe/Vienna", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring non-all-day event (with zoned DTEND equals DTSTART)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateTimeValue("20251011T040506", tzVienna)),
            DtEnd(dateTimeValue("20251011T040506", tzVienna))    // equals DTSTART, should be ignored
        ))
        builder.build(event, event, result)
        // default duration: 0 sec -> DTEND == DTSTART in calendar provider
        assertEquals(1760148306000, result.entityValues.get(Events.DTEND))
        assertEquals("Europe/Vienna", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring all-day event (with DURATION)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateValue("20251010")),
            Duration(Period.ofDays(3))
        ))
        builder.build(event, event, result)
        assertEquals(1760313600000, result.entityValues.get(Events.DTEND))
        assertEquals("UTC", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring all-day event (with negative DURATION)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateValue("20251010")),
            Duration(Period.ofDays(-3))     // invalid negative DURATION will be treated as positive
        ))
        builder.build(event, event, result)
        assertEquals(1760313600000, result.entityValues.get(Events.DTEND))
        assertEquals("UTC", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring non-all-day event (with DURATION)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateTimeValue("20251010T010203", tzVienna)),
            Duration(java.time.Duration.ofMinutes(90))
        ))
        builder.build(event, event, result)
        assertEquals(1760056323000, result.entityValues.get(Events.DTEND))
        assertEquals("Europe/Vienna", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring non-all-day event (with negative DURATION)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateTimeValue("20251010T010203", tzVienna)),
            Duration(java.time.Duration.ofMinutes(-90))     // invalid negative DURATION will be treated as positive
        ))
        builder.build(event, event, result)
        assertEquals(1760056323000, result.entityValues.get(Events.DTEND))
        assertEquals("Europe/Vienna", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring all-day event (neither DTEND nor DURATION)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateValue("20251010"))
        ))
        builder.build(event, event, result)
        // default duration 1 day
        assertEquals(1760140800000, result.entityValues.get(Events.DTEND))
        assertEquals("UTC", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }

    @Test
    fun `Non-recurring non-all-day event (neither DTEND nor DURATION)`() {
        val result = Entity(ContentValues())
        val event = VEvent(propertyListOf(
            DtStart(dateTimeValue("20251010T010203", tzVienna))
        ))
        builder.build(event, event, result)
        // default duration 0 seconds
        assertEquals(1760050923000, result.entityValues.get(Events.DTEND))
        assertEquals("Europe/Vienna", result.entityValues.get(Events.EVENT_END_TIMEZONE))
    }


    @Test
    fun `alignWithDtStart(endDate=DATE, startDate=DATE)`() {
        val endDate = dateValue("20251007")
        val startDate = dateValue("20250101")

        val result = builder.alignWithDtStart(endDate, startDate)

        assertEquals(endDate, result)
    }

    @Test
    fun `alignWithDtStart(endDate=DATE, startDate=DATE-TIME)`() {
        val endDate = dateValue("20251007")
        val startDate = dateTimeValue("20250101T005623", tzVienna)

        val result = builder.alignWithDtStart(endDate, startDate)

        assertEquals(dateTimeValue("20251007T005623", tzVienna), result)
    }

    @Test
    fun `alignWithDtStart(endDate=DATE, startDate=DATE-TIME (floating))`() {
        val endDate = dateValue("20251007")
        val startDate = dateTimeValue("20250101T005623")

        val result = builder.alignWithDtStart(endDate, startDate)

        assertEquals(dateTimeValue("20251007T005623", tzRule.defaultZoneId), result)
    }

    @Test
    fun `alignWithDtStart(endDate=DATE-TIME, startDate=DATE)`() {
        val endDate = dateTimeValue("20251007T010203Z")
        val startDate = dateValue("20250101")

        val result = builder.alignWithDtStart(endDate, startDate)

        assertEquals(dateValue("20251007"), result)
    }

    @Test
    fun `alignWithDtStart(endDate=DATE-TIME, startDate=DATE-TIME)`() {
        val endDate = dateTimeValue("20251007T010203Z")
        val startDate = dateTimeValue("20250101T045623", tzVienna)

        val result = builder.alignWithDtStart(endDate, startDate)

        assertEquals(endDate, result)
    }


    @Test
    fun `calculateFromDefault (DATE)`() {
        val startDate = dateValue("20251031")

        val result = builder.calculateFromDefault(startDate)

        assertEquals(dateValue("20251101"), result)
    }

    @Test
    fun `calculateFromDefault (DATE-TIME)`() {
        val startDate = dateTimeValue("20251031T123456Z")

        val result = builder.calculateFromDefault(startDate)

        assertEquals(startDate, result)
    }


    @Test
    fun `calculateFromDuration (startDate=DATE, duration is date-based)`() {
        val startDate = dateValue("20240228")
        val duration = java.time.Duration.ofDays(1)

        val result = builder.calculateFromDuration(startDate, duration)

        // leap day
        assertEquals(dateValue("20240229"), result)
    }

    @Test
    fun `calculateFromDuration (startDate=DATE, duration is time-based)`() {
        val startDate = dateValue("20241231")
        val duration = java.time.Duration.ofHours(25)

        val result = builder.calculateFromDuration(startDate, duration)

        assertEquals(dateValue("20250101"), result)
    }

    @Test
    fun `calculateFromDuration (startDate=DATE-TIME, duration is date-based)`() {
        val startDate = dateTimeValue("20250101T045623", tzVienna)
        val duration = java.time.Duration.ofDays(1)

        val result = builder.calculateFromDuration(startDate, duration)

        assertEquals(dateTimeValue("20250102T045623", tzVienna), result)
    }

    @Test
    fun `calculateFromDuration (startDate=DATE-TIME, duration is time-based)`() {
        val startDate = dateTimeValue("20250101T045623", tzVienna)
        val duration = java.time.Duration.ofHours(25)

        val result = builder.calculateFromDuration(startDate, duration)

        assertEquals(dateTimeValue("20250102T055623", tzVienna), result)
    }

    @Test
    fun `calculateFromDuration (startDate=DATE-TIME, duration is time-based and negative)`() {
        val startDate = dateTimeValue("20250101T045623", tzVienna)
        val duration = java.time.Duration.ofHours(-25)

        val result = builder.calculateFromDuration(startDate, duration)

        assertEquals(dateTimeValue("20250102T055623", tzVienna), result)
    }

}