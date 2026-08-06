/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import at.bitfire.synctools.util.AndroidTimeUtils
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.ExDate
import net.fortuna.ical4j.model.property.RDate
import net.fortuna.ical4j.model.property.RRule
import kotlin.jvm.optionals.getOrNull

class RecurrenceFieldsBuilder(
    private val allDayBuilder: AllDayBuilder
) : DavTaskEntityBuilder {

    override fun build(from: VToDo, to: Entity) = build(from, from, to)

    override fun build(from: VToDo, main: VToDo, to: Entity) {
        val rRule = from.getProperty<RRule<*>>(RRule.RRULE).getOrNull()
        val rDates = from.getProperties<RDate<*>>(RDate.RDATE)
        val recurring = rRule != null || rDates.isNotEmpty()

        if (recurring && from === main) {
            val allDay = to.entityValues.getAsBoolean(Tasks.IS_ALLDAY) ?: false
            val tz = if (allDay) null else allDayBuilder.referenceTimeZone(from)

            to.entityValues.put(Tasks.RRULE, rRule?.value)
            to.entityValues.put(Tasks.RDATE,
                if (rDates.isEmpty()) null else AndroidTimeUtils.recurrenceSetsToOpenTasksString(rDates, tz)
            )

            val exDates = from.getProperties<ExDate<*>>(ExDate.EXDATE)
            to.entityValues.put(Tasks.EXDATE,
                if (exDates.isEmpty()) null else AndroidTimeUtils.recurrenceSetsToOpenTasksString(exDates, tz)
            )
        } else {
            to.entityValues.putNull(Tasks.RRULE)
            to.entityValues.putNull(Tasks.RDATE)
            to.entityValues.putNull(Tasks.EXDATE)
        }
    }

}
