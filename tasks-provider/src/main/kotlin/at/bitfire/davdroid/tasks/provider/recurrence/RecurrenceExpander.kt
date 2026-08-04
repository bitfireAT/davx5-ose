/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.tasks.provider.recurrence

import net.fortuna.ical4j.model.Recur
import net.fortuna.ical4j.model.property.RRule
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.Temporal
import java.util.logging.Logger

/** Source fields of the main (non-exception) task row [RecurrenceExpander] expands. */
data class RecurringTaskSource(
    val taskId: Long,
    val dtstart: Long?,
    val dtstartTz: String?,
    val due: Long?,
    val duration: String?,
    val isAllDay: Boolean,
    val rrule: String?,
    val rdate: String?,
    val exdate: String?
)

/** Source fields of a RECURRENCE-ID override row (RFC 5545 §3.8.4.4) of a [RecurringTaskSource]. */
data class RecurrenceExceptionSource(
    val taskId: Long,
    val originalInstanceTime: Long,
    val dtstart: Long?,
    val dtstartTz: String?,
    val due: Long?,
    val duration: String?,
    val isAllDay: Boolean
)

/** One expanded occurrence, ready to become an [at.bitfire.tasks.contract.TaskInstances] row. */
data class InstanceCandidate(
    val taskId: Long,
    val instanceStart: Long?,
    val instanceStartSorting: Long?,
    val instanceDue: Long?,
    val instanceDueSorting: Long?
)

/**
 * Expands a task's RFC 5545 §3.8.5 RRULE/RDATE/EXDATE into concrete occurrences and folds in
 * RECURRENCE-ID overrides (§3.8.4.4) — the engine behind [at.bitfire.tasks.contract.TaskInstances]
 * (D3 of the design doc: the *provider* expands recurrence, not frontends).
 *
 * Deliberately bounded and deterministic — no dependency on "now", so it's cheap to fully re-derive
 * on every write instead of needing a background job to roll a time window forward. Expansion stops
 * at whichever of the RRULE's own COUNT/UNTIL, [MAX_INSTANCES], or [MAX_HORIZON] comes first. An
 * indefinitely-recurring task (no COUNT/UNTIL) only gets instances up to that horizon; this is the
 * same practical limit every mainstream calendar/task app imposes.
 */
object RecurrenceExpander {

    /** Hard cap on generated occurrences, regardless of what the RRULE itself specifies. */
    const val MAX_INSTANCES = 500

    /** How far past DTSTART an unbounded (no COUNT/UNTIL) RRULE gets expanded. */
    val MAX_HORIZON: Period = Period.ofYears(10)

    private val logger get() = Logger.getLogger(RecurrenceExpander::class.java.name)

    private val DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE                // yyyyMMdd
    private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun expand(main: RecurringTaskSource, exceptions: List<RecurrenceExceptionSource>): List<InstanceCandidate> {
        val dtstart = main.dtstart
        val recurring = !main.rrule.isNullOrEmpty() || !main.rdate.isNullOrEmpty()

        // non-recurring, or recurring without a usable seed (no DTSTART) - exactly one instance
        if (!recurring || dtstart == null)
            return listOf(singleInstance(main.taskId, dtstart, main.isAllDay, main.due, main.duration, timeZone(main.dtstartTz)))

        val zoneId = timeZone(main.dtstartTz)
        val seed = toTemporal(dtstart, main.isAllDay, zoneId)

        // RFC 5545 §3.8.5: the recurrence set is RRULE ∪ RDATE − EXDATE, all anchored on DTSTART
        val anchors = sortedSetOf<Long>()
        anchors += dtstart

        main.rrule?.takeIf { it.isNotEmpty() }?.let { rruleValue ->
            try {
                val recur = RRule<Temporal>(rruleValue).recur
                val periodEnd = addHorizon(seed, main.isAllDay)
                recur.getDates(seed, seed, periodEnd, MAX_INSTANCES).forEach { anchors += it.toEpochMilli(main.isAllDay) }
            } catch (e: Exception) {
                logger.warning("Can't expand RRULE '$rruleValue' for task ${main.taskId}, treating as non-recurring: $e")
            }
        }
        parseDateList(main.rdate, main.isAllDay, zoneId).forEach { anchors += it.toEpochMilli(main.isAllDay) }

        val excluded = parseDateList(main.exdate, main.isAllDay, zoneId).map { it.toEpochMilli(main.isAllDay) }.toSet()
        val remaining = anchors.filterNot { it in excluded }.take(MAX_INSTANCES)

        val exceptionsByAnchor = exceptions.associateBy { it.originalInstanceTime }
        val candidates = mutableListOf<InstanceCandidate>()
        for (anchor in remaining) {
            val exception = exceptionsByAnchor[anchor]
            candidates += if (exception != null)
                singleInstance(
                    exception.taskId, exception.dtstart, exception.isAllDay, exception.due, exception.duration,
                    timeZone(exception.dtstartTz)
                )
            else
                occurrenceInstance(main, anchor, zoneId)
        }

        // defensive: an exception whose RECURRENCE-ID no longer matches a generated anchor (e.g. the
        // RRULE changed after the exception was created) still gets its own row rather than vanishing
        val matchedIds = remaining.mapNotNull { exceptionsByAnchor[it]?.taskId }.toSet()
        for (exception in exceptions) {
            if (exception.taskId !in matchedIds)
                candidates += singleInstance(
                    exception.taskId, exception.dtstart, exception.isAllDay, exception.due, exception.duration,
                    timeZone(exception.dtstartTz)
                )
        }

        return candidates
    }


    // --- single (non-recurring or exception-row) occurrence ------------------------------------

    private fun singleInstance(
        taskId: Long, dtstart: Long?, isAllDay: Boolean, due: Long?, duration: String?, zoneId: ZoneId
    ): InstanceCandidate {
        val computedDue = due ?: durationMillis(duration)?.let { offset -> dtstart?.let { it + offset } }
        val startSorting = dtstart?.let { localNormalizedMillis(it, isAllDay, zoneId) }
        val dueSorting = if (startSorting != null && computedDue != null)
            startSorting + (computedDue - requireNotNull(dtstart))
        else
            null
        return InstanceCandidate(taskId, dtstart, startSorting, computedDue, dueSorting)
    }

    /** A recurring main task's own (non-overridden) occurrence at [anchorStart]. */
    private fun occurrenceInstance(main: RecurringTaskSource, anchorStart: Long, zoneId: ZoneId): InstanceCandidate {
        val dtstart = requireNotNull(main.dtstart)
        val dueOffsetMs = if (main.due != null) main.due - dtstart else durationMillis(main.duration)
        val instanceDue = dueOffsetMs?.let { anchorStart + it }
        val startSorting = localNormalizedMillis(anchorStart, main.isAllDay, zoneId)
        val dueSorting = dueOffsetMs?.let { startSorting + it }
        return InstanceCandidate(main.taskId, anchorStart, startSorting, instanceDue, dueSorting)
    }

    private fun durationMillis(duration: String?): Long? =
        duration?.let { runCatching { Duration.parse(it).toMillis() }.getOrNull() }


    // --- date/time helpers -----------------------------------------------------------------------

    private fun timeZone(tzId: String?): ZoneId =
        if (tzId.isNullOrEmpty() || tzId == "UTC")
            ZoneOffset.UTC
        else
            try {
                ZoneId.of(tzId)
            } catch (e: Exception) {
                logger.warning("Unknown time zone '$tzId', falling back to UTC: $e")
                ZoneOffset.UTC
            }

    private fun toTemporal(millis: Long, isAllDay: Boolean, zoneId: ZoneId): Temporal =
        if (isAllDay)
            Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        else
            Instant.ofEpochMilli(millis).atZone(zoneId)

    private fun Temporal.toEpochMilli(isAllDay: Boolean): Long =
        if (isAllDay)
            (this as LocalDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        else
            (this as ZonedDateTime).toInstant().toEpochMilli()

    private fun addHorizon(seed: Temporal, isAllDay: Boolean): Temporal =
        if (isAllDay) (seed as LocalDate).plus(MAX_HORIZON) else (seed as ZonedDateTime).plus(MAX_HORIZON)

    /**
     * Strips the real time zone and reinterprets the same wall-clock Y-M-D-H-M-S as UTC, so
     * `ORDER BY` gives consistent chronological-feeling results across tasks in different time
     * zones (a 9am-local task in Tokyo and a 9am-local task in New York both sort as "9am"),
     * matching what [at.bitfire.tasks.contract.TaskInstances.INSTANCE_START_SORTING] documents.
     */
    private fun localNormalizedMillis(millis: Long, isAllDay: Boolean, zoneId: ZoneId): Long =
        if (isAllDay)
            millis
        else
            Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDateTime().atZone(ZoneOffset.UTC).toInstant().toEpochMilli()

    /**
     * Parses the [at.bitfire.tasks.contract.Tasks.RDATE]/[at.bitfire.tasks.contract.Tasks.EXDATE]
     * column format: comma-separated `yyyyMMdd` (all-day) or `yyyyMMdd'T'HHmmss[Z]` (timed) values,
     * sharing [zoneId] with DTSTART (RFC 5545 §3.8.5 - RDATE/EXDATE values are in the same VALUE
     * type and, for local times, the same time zone as DTSTART).
     */
    private fun parseDateList(dbStr: String?, isAllDay: Boolean, zoneId: ZoneId): List<Temporal> {
        if (dbStr.isNullOrEmpty()) return emptyList()
        return dbStr.split(",").mapNotNull { token ->
            val trimmed = token.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            try {
                when {
                    isAllDay -> LocalDate.parse(trimmed, DATE_FORMAT)
                    trimmed.endsWith("Z") -> LocalDateTime.parse(trimmed.dropLast(1), DATE_TIME_FORMAT).atZone(ZoneOffset.UTC)
                    else -> LocalDateTime.parse(trimmed, DATE_TIME_FORMAT).atZone(zoneId)
                }
            } catch (e: Exception) {
                logger.warning("Can't parse recurrence date '$trimmed': $e")
                null
            }
        }
    }

}
