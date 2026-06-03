/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.util

import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.util.AndroidTimeUtils.toInstant
import at.bitfire.synctools.util.AndroidTimeUtils.toZonedDateTime
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.DateProperty
import net.fortuna.ical4j.model.property.DtStart
import net.fortuna.ical4j.model.property.Trigger
import java.time.Duration
import java.time.Instant
import java.time.temporal.TemporalAmount
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.jvm.optionals.getOrNull

object AlarmTriggerCalculator {
    private val logger = Logger.getLogger(javaClass.name)

    /**
     * Calculates the minutes before/after an event/task to know when a given alarm occurs.
     *
     * Note: Android's alarm granularity is minutes. This method calculates with milliseconds, but
     * the result is rounded down to minutes (seconds cut off).
     *
     * @param alarm         the alarm to calculate the minutes from
     * @param refStart      reference `DTSTART` from the calendar component
     * @param refEnd        reference `DTEND` (`VEVENT`) or `DUE` (`VTODO`) from the calendar component
     * @param allowRelEnd   *true*: caller accepts minutes related to the end;
     * *false*: caller only accepts minutes related to the start
     *
     * @return Pair of values:
     *
     * 1. whether the minutes are related to the start or end (always [Related.START] if [allowRelEnd] is *false*)
     * 2. number of minutes before start/end (negative value means number of minutes *after* start/end)
     *
     * May be *null* if there's not enough information to calculate the number of minutes.
     */
    fun alarmTriggerToMinutes(
        alarm: VAlarm,
        refStart: DtStart<*>?,
        refEnd: DateProperty<*>?,
        allowRelEnd: Boolean
    ): Pair<Related, Int>? {
        val trigger = alarm.getProperty<Trigger>(Property.TRIGGER).getOrNull() ?: return null

        val triggerRelated = trigger.getParameter<Related>(Parameter.RELATED).getOrNull() ?: Related.START
        val triggerDuration = trigger.duration
        val triggerTime: Instant? = trigger.date    // Trigger is a DateProperty<Instant>

        return if (triggerDuration != null) {
            triggerDurationToMinutes(
                triggerDuration,
                triggerRelated,
                refStart,
                refEnd,
                allowRelEnd
            )
        } else if (triggerTime != null && refStart?.date != null) {
            triggerTimeToMinutes(
                triggerTime,
                refStart
            )
        } else {
            logger.log(Level.WARNING, "VALARM TRIGGER type is not DURATION or DATE-TIME " +
                    "(requires event DTSTART for Android), ignoring alarm", alarm)
            null
        }
    }

    // TRIGGER value is a DURATION. Important:
    // 1) Negative values in TRIGGER mean positive values in Reminders.MINUTES and vice versa.
    // 2) Android doesn't know alarm seconds, but only minutes. Cut off seconds from the final result.
    // 3) DURATION can be a Duration (time-based) or a Period (date-based), which have to be treated differently.
    private fun triggerDurationToMinutes(
        triggerDuration: TemporalAmount,
        triggerRelated: Related,
        refStart: DtStart<*>?,
        refEnd: DateProperty<*>?,
        allowRelatedEnd: Boolean
    ): Pair<Related, Int>? {
        return when (triggerRelated) {
            Related.START -> {
                triggerRelatedStartToMinutes(triggerDuration, refStart)
            }
            Related.END if allowRelatedEnd -> {
                triggerRelatedEndToMinutes(triggerDuration, refEnd)
            }
            else -> {
                triggerRelatedEndToRelatedStartMinutes(triggerDuration, refStart, refEnd)
            }
        }
    }

    private fun triggerRelatedStartToMinutes(
        triggerDuration: TemporalAmount,
        refStart: DtStart<*>?
    ): Pair<Related, Int>? {
        val start = refStart?.normalizedDate()?.toZonedDateTime() ?: return null

        val alarmTime = start + triggerDuration
        val minutes = Duration.between(alarmTime, start).toMinutes().toInt()

        return Pair(Related.START, minutes)
    }

    private fun triggerRelatedEndToMinutes(
        triggerDuration: TemporalAmount,
        refEnd: DateProperty<*>?
    ): Pair<Related, Int>? {
        val end = refEnd?.normalizedDate()?.toZonedDateTime() ?: return null

        val alarmTime = end + triggerDuration
        val minutes = Duration.between(alarmTime, end).toMinutes().toInt()

        return Pair(Related.END, minutes)
    }

    private fun triggerRelatedEndToRelatedStartMinutes(
        triggerDuration: TemporalAmount,
        refStart: DtStart<*>?,
        refEnd: DateProperty<*>?
    ): Pair<Related, Int>? {
        val start = refStart?.normalizedDate()?.toZonedDateTime() ?: return null
        val end = refEnd?.normalizedDate()?.toZonedDateTime() ?: return null

        val alarmTime = end + triggerDuration
        val minutes = Duration.between(alarmTime, start).toMinutes().toInt()

        return Pair(Related.START, minutes)
    }

    // TRIGGER value is a DATE-TIME (UTC), calculate minutes from start time
    private fun triggerTimeToMinutes(
        triggerTime: Instant,
        refStart: DtStart<*>
    ): Pair<Related, Int> {
        val start = refStart.date.toInstant()
        val minutes = Duration.between(triggerTime, start).toMinutes().toInt()

        return Pair(Related.START, minutes)
    }
}
