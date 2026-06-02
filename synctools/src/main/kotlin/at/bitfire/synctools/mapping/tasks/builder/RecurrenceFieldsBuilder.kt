/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.ical4android.Task
import at.bitfire.synctools.icalendar.isAllDay
import at.bitfire.synctools.util.AndroidTimeUtils
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import org.dmfs.tasks.contract.TaskContract.Tasks
import kotlin.jvm.optionals.getOrNull

class RecurrenceFieldsBuilder : DmfsTaskFieldBuilder, DmfsTaskFieldBuilderVToDo {

    private val allDayBuilder = AllDayBuilder()

    override fun build(from: Task, to: Entity) {
        val allDay = from.isAllDay()
        val tz = if (allDay) null else allDayBuilder.getTimeZone(from)

        to.entityValues.put(Tasks.RRULE, from.rRule?.value)

        to.entityValues.put(Tasks.RDATE,
            if (from.rDates.isEmpty())
                null
            else
                AndroidTimeUtils.recurrenceSetsToOpenTasksString(from.rDates, tz)
        )

        to.entityValues.put(Tasks.EXDATE,
            if (from.exDates.isEmpty())
                null
            else
                AndroidTimeUtils.recurrenceSetsToOpenTasksString(from.exDates, tz)
        )
    }

    override fun build(from: VToDo, to: Entity) {
        val allDay = from.isAllDay()
        val tz = if (allDay) null else allDayBuilder.getTimeZone(from)

        val rRule = from.getProperty<RRule<*>>(RRule.RRULE).getOrNull()
        to.entityValues.put(Tasks.RRULE, rRule?.value)

        val rDates = from.getProperties<RDate<*>>(RDate.RDATE)
        to.entityValues.put(Tasks.RDATE,
            if (rDates.isEmpty())
                null
            else
                AndroidTimeUtils.recurrenceSetsToOpenTasksString(rDates, tz)
        )

        val exDates = from.getProperties<ExDate<*>>(ExDate.EXDATE)
        to.entityValues.put(Tasks.EXDATE,
            if (exDates.isEmpty())
                null
            else
                AndroidTimeUtils.recurrenceSetsToOpenTasksString(exDates, tz)
        )
    }

}
