/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.icalendar.propertyListOf
import at.bitfire.tasks.contract.TaskAlarms
import net.fortuna.ical4j.model.component.VAlarm
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.Related
import net.fortuna.ical4j.model.property.Action
import net.fortuna.ical4j.model.property.Description
import net.fortuna.ical4j.model.property.Duration
import net.fortuna.ical4j.model.property.Repeat
import net.fortuna.ical4j.model.property.Summary
import net.fortuna.ical4j.model.property.Trigger
import net.fortuna.ical4j.model.property.Uid
import java.time.Instant

/**
 * Reads [at.bitfire.tasks.contract.TaskAlarms] rows and builds [VAlarm]s. Alarm rows are
 * distinguished from [at.bitfire.tasks.contract.TaskProperties] sub-rows by the presence of
 * [TaskAlarms.ACTION] — a column that only exists in the `Alarms` table (see [DavTaskList.getTask]).
 */
class AlarmsHandler : DavTaskEntityHandler {

    override fun process(from: Entity, main: Entity, to: VToDo) {
        for (row in from.subValues.filter { it.values.containsKey(TaskAlarms.ACTION) }) {
            to += buildAlarm(row.values)
        }
    }

    private fun buildAlarm(values: ContentValues): VAlarm {
        // PropertyList is immutable (add() returns a copy) - build the full list in one
        // propertyListOf() call rather than incrementally, see Ical4jHelpers.plusAssign.
        val action = Action(
            when (values.getAsString(TaskAlarms.ACTION)) {
                TaskAlarms.Action.AUDIO -> Action.VALUE_AUDIO
                TaskAlarms.Action.EMAIL -> Action.VALUE_EMAIL
                else                    -> Action.VALUE_DISPLAY
            }
        )

        val optional = listOfNotNull(
            values.getAsString(TaskAlarms.DESCRIPTION)?.let { Description(it) },
            values.getAsString(TaskAlarms.SUMMARY)?.let { Summary(it) },
            values.getAsString(TaskAlarms.DURATION)?.let { Duration(it) },
            values.getAsInteger(TaskAlarms.REPEAT)?.let { Repeat(it) },
            values.getAsString(TaskAlarms.UID)?.let { Uid(it) },
            buildTrigger(values)
        )

        return VAlarm(propertyListOf(action, *optional.toTypedArray()))
    }

    private fun buildTrigger(values: ContentValues): Trigger? {
        val relative = values.getAsString(TaskAlarms.TRIGGER_RELATIVE)
        if (relative != null) {
            val duration = try {
                java.time.Duration.parse(relative)
            } catch (_: Exception) {
                return null
            }
            return Trigger().apply {
                this.duration = duration
                if (values.getAsString(TaskAlarms.TRIGGER_RELATED) == TaskAlarms.Related.END)
                    this += Related.END
            }
        }

        val absolute = values.getAsLong(TaskAlarms.TRIGGER_ABSOLUTE)
        if (absolute != null)
            return Trigger(Instant.ofEpochMilli(absolute))

        return null
    }

}
