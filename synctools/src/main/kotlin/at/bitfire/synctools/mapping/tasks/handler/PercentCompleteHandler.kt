/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.ical4android.Task
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.PercentComplete
import org.dmfs.tasks.contract.TaskContract.Tasks

class PercentCompleteHandler : DmfsTaskFieldHandler, DmfsTaskFieldHandler2 {

    override fun process(from: ContentValues, to: Task) {
        from.getAsInteger(Tasks.PERCENT_COMPLETE)?.let { percent ->
            to.percentComplete = percent
        }
    }

    override fun process(from: Entity, main: Entity, to: VToDo) {
        val percentComplete = from.entityValues.getAsInteger(Tasks.PERCENT_COMPLETE)
        if (percentComplete != null) {
            to += PercentComplete(percentComplete)
        }
    }
}
