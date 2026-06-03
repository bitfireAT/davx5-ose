/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.ical4android.Task
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Priority
import org.dmfs.tasks.contract.TaskContract.Tasks

class PriorityHandler : DmfsTaskFieldHandler, DmfsTaskFieldHandler2 {

    override fun process(from: ContentValues, to: Task) {
        from.getAsInteger(Tasks.PRIORITY)?.let { to.priority = it }
    }

    override fun process(from: Entity, main: Entity, to: VToDo) {
        val priority = from.entityValues.getAsInteger(Tasks.PRIORITY)
        if (priority != null && priority != Priority.VALUE_UNDEFINED) {
            to += Priority(priority)
        }
    }
}
