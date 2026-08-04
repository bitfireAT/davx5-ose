/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Status

class StatusHandler : DavTaskEntityHandler {
    override fun process(from: Entity, main: Entity, to: VToDo) {
        val status = when (from.entityValues.getAsString(Tasks.STATUS)) {
            Tasks.Status.IN_PROCESS -> Status(Status.VALUE_IN_PROCESS)
            Tasks.Status.COMPLETED  -> Status(Status.VALUE_COMPLETED)
            Tasks.Status.CANCELLED  -> Status(Status.VALUE_CANCELLED)
            else                    -> Status(Status.VALUE_NEEDS_ACTION)
        }
        to += status
    }
}
