/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.synctools.icalendar.isAllDay
import at.bitfire.synctools.util.AndroidTimeUtils
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import org.dmfs.tasks.contract.TaskContract.Tasks
import kotlin.jvm.optionals.getOrNull

class RecurrenceFieldsBuilder : DmfsTaskEntityBuilder {

    private val allDayBuilder = AllDayBuilder()

    override fun build(from: VToDo, to: Entity) = build(from, from, to)

    override fun build(from: VToDo, main: VToDo, to: Entity) {
        val rRule = from.getProperty<RRule<*>>(RRule.RRULE).getOrNull()
        val rDates = from.getProperties<RDate<*>>(RDate.RDATE)
        val recurring = rRule != null || rDates.isNotEmpty()

        if (recurring && from === main) {
            val allDay = from.isAllDay()
            val tz = if (allDay) null else allDayBuilder.getTimeZone(from)

            to.entityValues.put(Tasks.RRULE, rRule?.value)

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
        } else {
            to.entityValues.putNull(Tasks.RRULE)
            to.entityValues.putNull(Tasks.RDATE)
            to.entityValues.putNull(Tasks.EXDATE)
        }
    }

}
