/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.mapping.tasks.handler.TaskTimeField
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.DtStart

class StartTimeHandler : DavTaskEntityHandler {
    override fun process(from: Entity, main: Entity, to: VToDo) {
        val epochMillis = from.entityValues.getAsLong(Tasks.DTSTART) ?: return
        val allDay = from.entityValues.getAsBoolean(Tasks.IS_ALLDAY) ?: false
        val tzId = from.entityValues.getAsString(Tasks.DTSTART_TZ)
        to += DtStart(TaskTimeField(epochMillis, tzId, allDay).toTemporal())
    }
}
