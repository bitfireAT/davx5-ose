/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.synctools.exception.InvalidLocalResourceException
import at.bitfire.synctools.util.AndroidTimeUtils
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import at.bitfire.synctools.util.AndroidTimeUtils.toZonedDateTime
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.ExRule
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.Temporal
import java.util.logging.Level
import java.util.logging.Logger

class RecurrenceFieldsHandler: AndroidEventFieldHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: VEvent) {
        val values = from.entityValues

        val tsStart = values.getAsLong(Events.DTSTART) ?: throw InvalidLocalResourceException("Found event without DTSTART")
        val allDay = (values.getAsInteger(Events.ALL_DAY) ?: 0) != 0

        // provide start date as temporal
        val startDate by lazy {
            AndroidTimeField(
                timestamp = tsStart,
                timeZone = values.getAsString(Events.EVENT_TIMEZONE),
                allDay = allDay
            ).toTemporal()
        }

        // Note: big method – maybe split?

        // process RRULE field
        val rRules = mutableListOf<RRule<Temporal>>()
        values.getAsString(Events.RRULE)?.let { rRuleField ->
            try {
                for (rule in rRuleField.split(RECURRENCE_RULE_SEPARATOR)) {
                    val rule = RRule<Temporal>(rule)

                    // align RRULE UNTIL to DTSTART, if needed
                    rule.recur = alignUntil(rule.recur, startDate)

                    // skip if UNTIL is before event's DTSTART
                    val tsUntil = rule.recur.until?.toTimestamp()
                    if (tsUntil != null && tsUntil <= tsStart) {
                        logger.warning("Ignoring $rule because UNTIL ($tsUntil) is not after DTSTART ($tsStart)")
                        continue
                    }

                    rRules += rule
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse RRULE field, ignoring", e)
            }
        }

        // process RDATE field
        val rDates = mutableListOf<RDate<*>>()
        values.getAsString(Events.RDATE)?.let { rDateField ->
            try {
                AndroidTimeUtils.androidStringToRecurrenceSet(rDateField, allDay, Instant.ofEpochMilli(tsStart)) {
                    RDate(it)
                }?.let { rDates += it }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse RDATE field, ignoring", e)
            }
        }

        // EXRULE
        val exRules = mutableListOf<ExRule<Temporal>>()
        values.getAsString(Events.EXRULE)?.let { exRuleField ->
            try {
                for (rule in exRuleField.split(RECURRENCE_RULE_SEPARATOR)) {
                    val rule = ExRule<Temporal>(null, rule)

                    // align RRULE UNTIL to DTSTART, if needed
                    rule.recur = alignUntil(rule.recur, startDate)

                    // skip if UNTIL is before event's DTSTART
                    val tsUntil = rule.recur.until?.toTimestamp()
                    if (tsUntil != null && tsUntil <= tsStart) {
                        logger.warning("Ignoring $rule because UNTIL ($tsUntil) is not after DTSTART ($tsStart)")
                        continue
                    }

                    exRules += rule
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse recurrence rules, ignoring", e)
            }
        }

        // EXDATE
        val exDates = mutableListOf<ExDate<*>>()
        values.getAsString(Events.EXDATE)?.let { exDateField ->
            try {
                AndroidTimeUtils.androidStringToRecurrenceSet(exDateField,  allDay) { ExDate(it) }?.let {
                    exDates += it
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse recurrence rules, ignoring", e)
            }
        }

        // generate recurrence properties only for recurring main events
        val recurring = rRules.isNotEmpty() || rDates.isNotEmpty()
        if (from === main && recurring) {
            to.addAll<VEvent>(rRules + rDates + exRules + exDates)
        }
    }

    /**
     * Aligns the `UNTIL` of the given recurrence info to the VALUE-type (DATE-TIME/DATE) of [startTemporal].
     *
     * If the aligned `UNTIL` is a DATE-TIME, this method also makes sure that it's specified in UTC format
     * as required by RFC 5545 3.3.10.
     *
     * @param recur             recurrence info whose `UNTIL` shall be aligned
     * @param startTemporal     `DTSTART` date to compare with
     *
     * @return
     *
     * - UNTIL not set → original recur
     * - UNTIL and DTSTART are both either DATE or DATE-TIME → original recur
     * - UNTIL is DATE, DTSTART is DATE-TIME → UNTIL is amended to DATE-TIME with time and timezone from DTSTART
     * - UNTIL is DATE-TIME, DTSTART is DATE → UNTIL is reduced to its date component
     *
     * @see at.bitfire.synctools.mapping.calendar.builder.EndTimeBuilder.alignWithDtStart
     */
    fun alignUntil(recur: Recur<Temporal>, startTemporal: Temporal): Recur<Temporal> {
        val until: Temporal = recur.until ?: return recur

        if (DateUtils.isDateTime(until)) {
            // UNTIL is DATE-TIME
            if (DateUtils.isDateTime(startTemporal)) {
                // DTSTART is DATE-TIME → ensure UNTIL is in UTC
                val untilZoned = until.toZonedDateTime()
                return if (untilZoned.zone == ZoneOffset.UTC) {
                    recur
                } else {
                    Recur.Builder(recur)
                        .until(untilZoned.withZoneSameInstant(ZoneOffset.UTC).toInstant())
                        .build()
                }
            } else {
                // DTSTART is DATE → only take date part for UNTIL
                val untilDate = until.toLocalDate()
                return Recur.Builder(recur)
                    .until(untilDate)
                    .build()
            }
        } else {
            // UNTIL is DATE
            if (DateUtils.isDateTime(startTemporal)) {
                // DTSTART is DATE-TIME → amend UNTIL to UTC DATE-TIME
                val untilDate = until.toLocalDate()
                val startTime = startTemporal.toZonedDateTime()
                val untilDateWithTime = ZonedDateTime.of(untilDate, startTime.toLocalTime(), startTime.zone)
                return Recur.Builder(recur)
                    .until(untilDateWithTime.toInstant()) // convert to Instant for UTC with "Z" suffix
                    .build()
            } else {
                // DTSTART is DATE
                return recur
            }
        }
    }

    companion object {

        /**
         * Used to separate multiple RRULEs/EXRULEs in the RRULE/EXRULE storage field.
         */
        private const val RECURRENCE_RULE_SEPARATOR = "\n"

    }

}