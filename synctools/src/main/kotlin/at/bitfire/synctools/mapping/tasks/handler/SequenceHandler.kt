/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Sequence
import org.dmfs.tasks.contract.TaskContract.Tasks

class SequenceHandler : DmfsTaskFieldHandler, DmfsTaskFieldHandler2 {

    override fun process(from: ContentValues, to: Task) {
        to.sequence = from.getAsInteger(Tasks.SYNC_VERSION)
    }

    override fun process(from: Entity, main: Entity, to: VToDo) {
        val sequence = from.entityValues.getAsInteger(Tasks.SYNC_VERSION)
        if (sequence != null) {
            to += Sequence(sequence)
        }
    }
}
