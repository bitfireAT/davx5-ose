/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.exception.InvalidLocalResourceException
import at.bitfire.synctools.util.AndroidTimeUtils
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import at.bitfire.synctools.util.RecurrenceUtils
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.ExRule
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import java.time.Instant
import java.time.temporal.Temporal
import java.util.logging.Level
import java.util.logging.Logger

class RecurrenceFieldsHandler : AndroidEventEntityHandler {

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
                    rule.recur = RecurrenceUtils.alignUntil(rule.recur, startDate)

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
                    rule.recur = RecurrenceUtils.alignUntil(rule.recur, startDate)

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
                AndroidTimeUtils.androidStringToRecurrenceSet(exDateField, allDay) { ExDate(it) }?.let {
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

    companion object {

        /**
         * Used to separate multiple RRULEs/EXRULEs in the RRULE/EXRULE storage field.
         */
        private const val RECURRENCE_RULE_SEPARATOR = "\n"

    }

}
