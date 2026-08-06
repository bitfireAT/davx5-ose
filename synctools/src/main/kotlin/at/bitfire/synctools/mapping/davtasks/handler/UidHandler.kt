/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Uid

class UidHandler : DavTaskEntityHandler {
    override fun process(from: Entity, main: Entity, to: VToDo) {
        // Always take UID from main task
        main.entityValues.getAsString(Tasks._UID)?.let { to += Uid(it) }
    }
}
