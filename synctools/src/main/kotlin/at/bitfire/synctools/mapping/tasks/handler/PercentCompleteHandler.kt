/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.PercentComplete
import org.dmfs.tasks.contract.TaskContract.Tasks

class PercentCompleteHandler : DmfsTaskEntityHandler {

    override fun process(from: Entity, main: Entity, to: VToDo) {
        val percentComplete = from.entityValues.getAsInteger(Tasks.PERCENT_COMPLETE)
        if (percentComplete != null) {
            to += PercentComplete(percentComplete)
        }
    }
}
