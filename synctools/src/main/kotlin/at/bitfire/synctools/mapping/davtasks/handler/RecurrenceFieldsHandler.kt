/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.mapping.tasks.handler.TaskTimeField
import at.bitfire.synctools.util.AndroidTimeUtils
import at.bitfire.synctools.util.AndroidTimeUtils.isUtcTzId
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import at.bitfire.synctools.util.RecurrenceUtils
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import java.time.temporal.Temporal
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Reads [Tasks.RRULE], [Tasks.RDATE], [Tasks.EXDATE] and populates the given [VToDo].
 *
 * RDATE/EXDATE are stored as floating DATE-TIME strings with the timezone stored separately in
 * [Tasks.DTSTART_TZ] (the DTSTART timezone is the recurrence reference timezone, see
 * [at.bitfire.synctools.mapping.davtasks.builder.AllDayBuilder]). [withTzPrefix] prepends the
 * required `{tzId};` prefix before parsing.
 */
class RecurrenceFieldsHandler : DavTaskEntityHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: VToDo) {
        if (from !== main) return

        val allDay = from.entityValues.getAsBoolean(Tasks.IS_ALLDAY) ?: false
        val tzId = from.entityValues.getAsString(Tasks.DTSTART_TZ)
        val tsStart = from.entityValues.getAsLong(Tasks.DTSTART)

        val startTemporal: Temporal? by lazy {
            tsStart?.let { TaskTimeField(timestamp = it, tzId = tzId, allDay = allDay).toTemporal() }
        }

        from.entityValues.getAsString(Tasks.RRULE)?.let { rRuleStr ->
            try {
                var rule = RRule<Temporal>(rRuleStr)
                if (startTemporal != null) {
                    rule = RRule(RecurrenceUtils.alignUntil(rule.recur, startTemporal!!))
                    val tsUntil = rule.recur.until?.toTimestamp()
                    if (tsUntil != null && tsUntil <= tsStart!!) {
                        logger.warning("Ignoring $rule because UNTIL ($tsUntil) is not after DTSTART ($tsStart)")
                        return@let
                    }
                }
                to += rule
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse RRULE field, ignoring", e)
            }
        }

        from.entityValues.getAsString(Tasks.RDATE)?.let { rDateStr ->
            try {
                AndroidTimeUtils.androidStringToRecurrenceSet(withTzPrefix(rDateStr, tzId), allDay) { RDate(it) }
                    ?.let { to += it }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse RDATE field, ignoring", e)
            }
        }

        from.entityValues.getAsString(Tasks.EXDATE)?.let { exDateStr ->
            try {
                AndroidTimeUtils.androidStringToRecurrenceSet(withTzPrefix(exDateStr, tzId), allDay) { ExDate(it) }
                    ?.let { to += it }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't parse EXDATE field, ignoring", e)
            }
        }
    }

    internal fun withTzPrefix(recurrenceStr: String, tzId: String?): String {
        if (recurrenceStr.contains(';') || tzId == null || isUtcTzId(tzId))
            return recurrenceStr
        return "$tzId;$recurrenceStr"
    }

}
