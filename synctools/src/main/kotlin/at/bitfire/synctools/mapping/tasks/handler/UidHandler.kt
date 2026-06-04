/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.ical4android.Task
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Uid
import org.dmfs.tasks.contract.TaskContract.Tasks

class UidHandler : DmfsTaskFieldHandler, DmfsTaskFieldHandler2 {

    override fun process(from: ContentValues, to: Task) {
        to.uid = from.getAsString(Tasks._UID)
    }

    override fun process(from: Entity, main: Entity, to: VToDo) {
        val uid = from.entityValues.getAsString(Tasks._UID)
        if (uid != null) {
            to += Uid(uid)
        }
    }

}
