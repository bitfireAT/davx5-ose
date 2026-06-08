/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Due
import org.dmfs.tasks.contract.TaskContract.Tasks

class DueHandler : DmfsTaskFieldHandler, DmfsTaskFieldHandler2 {

    override fun process(from: ContentValues, to: Task) {
        val epochMillis = from.getAsLong(Tasks.DUE) ?: return

        val allDay = (from.getAsInteger(Tasks.IS_ALLDAY) ?: 0) != 0
        val tzId = from.getAsString(Tasks.TZ)

        to.due = Due(TaskTimeField(epochMillis, tzId, allDay).toTemporal())
    }

    override fun process(from: Entity, main: Entity, to: VToDo) {
        val epochMillis = from.entityValues.getAsLong(Tasks.DUE) ?: return

        val allDay = (from.entityValues.getAsInteger(Tasks.IS_ALLDAY) ?: 0) != 0
        val tzId = from.entityValues.getAsString(Tasks.TZ)

        to += Due(TaskTimeField(epochMillis, tzId, allDay).toTemporal())
    }
}
