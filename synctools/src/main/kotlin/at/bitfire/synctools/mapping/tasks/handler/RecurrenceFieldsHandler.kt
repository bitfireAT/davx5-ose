/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import at.bitfire.ical4android.Task
import at.bitfire.ical4android.util.DateUtils
import at.bitfire.ical4android.util.TimeApiExtensions.toLocalDate
import at.bitfire.synctools.util.AndroidTimeUtils
import at.bitfire.synctools.util.AndroidTimeUtils.isUtcTzId
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import at.bitfire.synctools.util.AndroidTimeUtils.toZonedDateTime
import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.Temporal
import java.util.logging.Level
import java.util.logging.Logger

class RecurrenceFieldsHandler : DmfsTaskFieldHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: ContentValues, to: Task) {
        val allDay = (from.getAsInteger(Tasks.IS_ALLDAY) ?: 0) != 0
        val tzId = from.getAsString(Tasks.TZ)

        val tsStart = from.getAsLong(Tasks.DTSTART)

        // provide start temporal lazily (only computed when UNTIL alignment is needed)
        val startTemporal: Temporal? by lazy {
            tsStart?.let { TaskTimeField(
                timestamp = it,
                tzId = tzId,
                allDay = allDay
            ).toTemporal() }
        }

        // process RRULE field
        from.getAsString(Tasks.RRULE)?.let { rRuleStr ->
            try {
                var rule = RRule<Temporal>(rRuleStr)

                // align RRULE UNTIL to DTSTART, if needed
                if (startTemporal != null) {
                    rule = RRule(alignUntil(rule.recur, startTemporal!!))

                    // skip if UNTIL is before task's DTSTART
                    val tsUntil = rule.recur.until?.toTimestamp()
                    if (tsUntil != null && tsUntil <= tsStart!!) {
                        logger.warning("Ignoring $rule because UNTIL ($tsUntil) is not after DTSTART ($tsStart)")
                        return@let
                    }
                }

                to.rRule = rule
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse RRULE field, ignoring", e)
            }
        }

        // process RDATE field
        from.getAsString(Tasks.RDATE)?.let { rDateStr ->
            try {
                AndroidTimeUtils.androidStringToRecurrenceSet(withTzPrefix(rDateStr, tzId), allDay) { RDate(it) }
                    ?.let { to.rDates += it }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse RDATE field, ignoring", e)
            }
        }

        // process EXDATE field
        from.getAsString(Tasks.EXDATE)?.let { exDateStr ->
            try {
                AndroidTimeUtils.androidStringToRecurrenceSet(withTzPrefix(exDateStr, tzId), allDay) { ExDate(it) }
                    ?.let { to.exDates += it }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse EXDATE field, ignoring", e)
            }
        }
    }

    /**
     * Prepends a `TZID;<tzId>` prefix to a recurrence set string if it contains floating DATE-TIMEs.
     *
     * OpenTasks stores RDATE/EXDATE as floating DATE-TIME strings (no trailing `Z`, no `TZID;` prefix)
     * when the timezone is stored separately in [Tasks.TZ]. [AndroidTimeUtils.androidStringToRecurrenceSet]
     * requires a `TZID;` prefix to parse such values. This method adds it when needed.
     *
     * @param recurrenceStr the RDATE/EXDATE value from the provider (e.g. `20251010T010203`)
     * @param tzId          the timezone ID from [Tasks.TZ], or null if not set
     * @return the string with `TZID;<tzId>;` prepended if the input is a floating DATE-TIME, otherwise unchanged
     */
    internal fun withTzPrefix(recurrenceStr: String, tzId: String?): String {
        // Already has a TZID; prefix or is a UTC/date-only value — return as-is
        if (recurrenceStr.contains(';') || tzId == null || isUtcTzId(tzId))
            return recurrenceStr
        return "$tzId;$recurrenceStr"
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
     * @see at.bitfire.synctools.mapping.calendar.handler.RecurrenceFieldsHandler.alignUntil
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

}
