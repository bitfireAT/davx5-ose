/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.util

import at.bitfire.dateTimeValue
import at.bitfire.dateValue
import at.bitfire.synctools.icalendar.requireDtStart
import at.bitfire.synctools.util.AlarmTriggerCalculator.alarmTriggerToMinutes
import net.fortuna.ical4j.data.CalendarBuilder
import net.fortuna.ical4j.model.Component
import net.fortuna.ical4j.model.Property.TRIGGER
import net.fortuna.ical4j.model.TimeZoneRegistryFactory
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.DateProperty
import net.fortuna.ical4j.model.property.DtEnd
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Due
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.Trigger
import net.fortuna.ical4j.util.CompatibilityHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.StringReader
import java.time.Instant
import java.time.Period
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.Temporal
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import java.time.Duration as JavaDuration

class AlarmTriggerCalculatorTest {

    private val tzRegistry = TimeZoneRegistryFactory.getInstance().createRegistry()
    private val tzVienna = tzRegistry.getTimeZone("Europe/Vienna")

    // current time stamp
    private val currentTime = ZonedDateTime.now()

    @Test
    fun `negative trigger duration`() {
        val alarm = VAlarm(JavaDuration.parse("-P1DT1H1M29S"))
        val refStart = DtStart<Temporal>(currentTime)

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = null,
            allowRelEnd = false
        )!!

        assertEquals(Related.START, ref)
        assertEquals((1.days + 1.hours + 1.minutes).toMinutes(), min)
    }

    @Test
    fun `trigger duration in seconds`() {
        val alarm = VAlarm(JavaDuration.parse("-PT3600S"))
        val refStart = DtStart<Temporal>(currentTime)

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = null,
            allowRelEnd = false
        )!!

        assertEquals(Related.START, ref)
        assertEquals(3600.seconds.toMinutes(), min)
    }

    @Test
    fun `positive trigger duration`() {
        val alarm = VAlarm(JavaDuration.parse("P1DT1H1M30S"))
        val refStart = DtStart<Temporal>(currentTime)

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = null,
            allowRelEnd = false
        )!!

        assertEquals(Related.START, ref)
        assertEquals(-(1.days + 1.hours + 1.minutes).toMinutes(), min)
    }

    @Test
    fun `trigger relative to end with allowRelEnd=true`() {
        val alarm = VAlarm(JavaDuration.parse("-P1DT1H1M30S")).apply {
            getRequiredProperty<Trigger>(TRIGGER).add<Trigger>(Related.END)
        }
        val refEnd = DtStart<Temporal>(currentTime)
        val allowRelEnd = true

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = null,
            refEnd = refEnd,
            allowRelEnd = allowRelEnd
        )!!

        assertEquals(Related.END, ref)
        assertEquals(60 * 24 + 60 + 1, min)
    }

    @Test
    fun `trigger relative to end with allowRelEnd=false`() {
        val alarm = VAlarm(JavaDuration.parse("-PT65S")).apply {
            getRequiredProperty<Trigger>(TRIGGER).add<Trigger>(Related.END)
        }
        val refStart = DtStart(currentTime)
        val refEnd = DtEnd(currentTime.plusSeconds(180))
        val allowRelEnd = false

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = refEnd,
            allowRelEnd = allowRelEnd
        )!!

        assertEquals(Related.START, ref)
        // duration of event: 180 s (3 min), 65 s before that -> alarm 1:55 min before start
        assertEquals(-1, min)
    }

    @Test
    fun `trigger relative to end without start time and with allowRelEnd=false`() {
        val alarm = VAlarm(JavaDuration.parse("-PT65S")).apply {
            getRequiredProperty<Trigger>(TRIGGER).add<Trigger>(Related.END)
        }
        val refStart = null
        val refEnd = DtEnd(currentTime)
        val allowRelEnd = false

        val result = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = refEnd,
            allowRelEnd = allowRelEnd
        )

        assertNull(result)
    }

    @Test
    fun `trigger relative to end without end time or duration and with allowRelEnd=false`() {
        val alarm = VAlarm(JavaDuration.parse("-PT65S")).apply {
            getRequiredProperty<Trigger>(TRIGGER).add<Trigger>(Related.END)
        }
        val refStart = DtStart(currentTime)
        val allowRelEnd = false

        val result = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = null,
            allowRelEnd = allowRelEnd
        )

        assertNull(result)
    }

    @Test
    fun `trigger relative to end and after end date with allowRelEnd=false`() {
        val alarm = VAlarm(JavaDuration.parse("P1DT1H1M30S")).apply {
            getRequiredProperty<Trigger>(TRIGGER).add<Trigger>(Related.END)
        }
        val refStart = DtStart(currentTime)
        // 90 sec (should be rounded down to 1 min) later
        val refEnd = Due(currentTime.plusSeconds(90))
        val allowRelEnd = false

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = refEnd,
            allowRelEnd = allowRelEnd
        )!!

        assertEquals(Related.START, ref)
        assertEquals(-(1.days.toMinutes() + 1.hours.toMinutes() + 1 + 1) /* duration of event: */ - 1, min)
    }

    @Test
    fun `trigger with Period instance`() {
        val alarm = VAlarm(Period.parse("-P1W1D"))
        val refStart = DtStart(dateTimeValue("20260508T120000", tzVienna))

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = null,
            allowRelEnd = false
        )!!

        assertEquals(Related.START, ref)
        assertEquals(8.days.toMinutes(), min)
    }

    @Test
    fun `trigger with DATE-TIME value`() {
        // 89 sec (should be cut off to 1 min) before event
        val alarm = VAlarm(currentTime.minusSeconds(89).toInstant()).apply {
            // not useful for DATE-TIME values, should be ignored
            getRequiredProperty<Trigger>(TRIGGER).add<Trigger>(Related.END)
        }

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = DtStart(currentTime),
            refEnd = null,
            allowRelEnd = true
        )!!

        assertEquals(Related.START, ref)
        assertEquals(1, min)
    }

    @Test
    fun `trigger with floating DATE-TIME value parsed in relaxed mode`() {
        /* In relaxed mode, ical4j accepts a floating DATE-TIME as TRIGGER,
        although RFC 5545 requires UTC-formatted DATE-TIME. */
        assumeTrue(CompatibilityHints.isHintEnabled(CompatibilityHints.KEY_RELAXED_PARSING))

        val event = CalendarBuilder().build(
            StringReader(
                "BEGIN:VCALENDAR\n" +
                        "VERSION:2.0\n" +
                        "BEGIN:VEVENT\n" +
                        "DTSTART:20260602T120000Z\n" +
                        "BEGIN:VALARM\n" +
                        "TRIGGER;VALUE=DATE-TIME:20260602T115800\n" +   // floating DATE-TIME, treated as UTC
                        "END:VALARM\n" +
                        "END:VEVENT\n" +
                        "END:VCALENDAR"
            )
        ).getComponent<VEvent>(Component.VEVENT).get()
        val alarm = event.alarms.first()
        val triggerDate = (alarm.getRequiredProperty<Trigger>(TRIGGER) as DateProperty<*>).date
        assertTrue(triggerDate is Instant)

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = event.requireDtStart<Temporal>(),    // UTC DATE-TIME too
            refEnd = null,
            allowRelEnd = false
        )!!

        assertEquals(Related.START, ref)
        assertEquals(2, min)    // (2026/06/02) 12:00 minus 11:58
    }

    @Test
    fun `refStart has DATE value`() {
        val alarm = VAlarm(JavaDuration.parse("-PT5M"))
        val refStart = DtStart(dateValue("20260407"))

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = null,
            allowRelEnd = true
        )!!

        assertEquals(Related.START, ref)
        assertEquals(5, min)
    }

    @Test
    fun `trigger related to end with refStart and refDate having DATE values and allowRelEnd=false`() {
        val alarm = VAlarm(Duration("-PT5M").duration).apply {
            getRequiredProperty<Trigger>(TRIGGER).add<Trigger>(Related.END)
        }
        val refStart = DtStart(dateValue("20260407"))
        val refEnd = DtStart(dateValue("20260408"))
        val allowRelEnd = false

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = refEnd,
            allowRelEnd = allowRelEnd
        )!!

        assertEquals(Related.START, ref)
        assertEquals(-(1.days - 5.minutes).toMinutes(), min)
    }

    @Test
    fun `trigger with DATE-TIME value and refStart with DATE value`() {
        val alarm = VAlarm(dateTimeValue("20260406T120000", ZoneOffset.UTC).toInstant())
        val refStart = DtStart(dateValue("20260407"))

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = null,
            allowRelEnd = true
        )!!

        assertEquals(Related.START, ref)
        assertEquals(12.hours.toMinutes(), min)
    }

    @Test
    fun `trigger relative to start with Period instance spanning DST change`() {
        // Event start: 2020/03/30 01:00 Vienna, alarm: one day before start of the event
        // DST changes on 2020/03/29 02:00 -> 03:00, so there is one hour less!
        // The alarm has to be set 23 hours before the event so that it is set one day earlier.
        val alarm = VAlarm(Period.parse("-P1D"))
        val refStart = DtStart<Temporal>(dateTimeValue("20200330T010000", tzVienna))

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = null,
            allowRelEnd = false
        )!!

        assertEquals(Related.START, ref)
        assertEquals(23.hours.toMinutes(), min)
    }

    @Test
    fun `trigger relative to end with Period instance spanning DST change`() {
        // Event end: 2020/03/30 01:00 Vienna, alarm: one day before end of the event
        // DST changes on 2020/03/29 02:00 -> 03:00, so there is one hour less!
        // The alarm has to be set 23 hours before the end of the event.
        val alarm = VAlarm(Period.parse("-P1D")).apply {
            getRequiredProperty<Trigger>(TRIGGER).add<Trigger>(Related.END)
        }
        val refEnd = DtEnd<Temporal>(dateTimeValue("20200330T010000", tzVienna))

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = null,
            refEnd = refEnd,
            allowRelEnd = true
        )!!

        assertEquals(Related.END, ref)
        assertEquals(23.hours.toMinutes(), min)
    }

    @Test
    fun `trigger relative to end with Period instance spanning DST change and allowRelEnd=false`() {
        // Event end: 2020/03/30 01:00 Vienna, alarm: one day before end of the event
        // DST changes on 2020/03/29 02:00 -> 03:00, so there is one hour less!
        val alarm = VAlarm(Period.parse("-P1D")).apply {
            getRequiredProperty<Trigger>(TRIGGER).add<Trigger>(Related.END)
        }
        val refStart = DtStart<Temporal>(dateTimeValue("20200330T000000", tzVienna))
        val refEnd = DtEnd<Temporal>(dateTimeValue("20200330T010000", tzVienna))

        val (ref, min) = alarmTriggerToMinutes(
            alarm = alarm,
            refStart = refStart,
            refEnd = refEnd,
            allowRelEnd = false
        )!!

        assertEquals(Related.START, ref)
        assertEquals(22.hours.toMinutes(), min)
    }
}

private fun kotlin.time.Duration.toMinutes(): Int = inWholeMinutes.toInt()
