/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.ical4android.Task
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Status
import net.fortuna.ical4j.model.property.immutable.ImmutableStatus
import org.dmfs.tasks.contract.TaskContract.Tasks

class StatusHandler : DmfsTaskFieldHandler, DmfsTaskFieldHandler2 {

    override fun process(from: ContentValues, to: Task) {
        to.status = when (from.getAsInteger(Tasks.STATUS)) {
            Tasks.STATUS_IN_PROCESS -> Status(Status.VALUE_IN_PROCESS)
            Tasks.STATUS_COMPLETED ->  Status(Status.VALUE_COMPLETED)
            Tasks.STATUS_CANCELLED ->  Status(Status.VALUE_CANCELLED)
            else ->                    Status(Status.VALUE_NEEDS_ACTION)
        }
    }

    override fun process(from: Entity, main: Entity, to: VToDo) {
        val status = when (from.entityValues.getAsInteger(Tasks.STATUS)) {
            Tasks.STATUS_IN_PROCESS -> Status(ImmutableStatus.VALUE_IN_PROCESS)
            Tasks.STATUS_COMPLETED -> Status(ImmutableStatus.VALUE_COMPLETED)
            Tasks.STATUS_CANCELLED -> Status(ImmutableStatus.VALUE_CANCELLED)
            else -> Status(ImmutableStatus.VALUE_NEEDS_ACTION)
        }
        to += status
    }

}
