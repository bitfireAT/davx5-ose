/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.mapping.tasks.handler.TaskTimeField
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Due

class DueHandler : DavTaskEntityHandler {
    override fun process(from: Entity, main: Entity, to: VToDo) {
        val epochMillis = from.entityValues.getAsLong(Tasks.DUE) ?: return
        val allDay = from.entityValues.getAsBoolean(Tasks.IS_ALLDAY) ?: false
        val tzId = from.entityValues.getAsString(Tasks.DUE_TZ)
        to += Due(TaskTimeField(epochMillis, tzId, allDay).toTemporal())
    }
}
