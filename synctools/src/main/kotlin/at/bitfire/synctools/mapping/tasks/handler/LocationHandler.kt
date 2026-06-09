/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Location
import org.dmfs.tasks.contract.TaskContract.Tasks

class LocationHandler : DmfsTaskEntityHandler {

    override fun process(from: Entity, main: Entity, to: VToDo) {
        val location = from.entityValues.getAsString(Tasks.LOCATION)
        if (location != null) {
            to += Location(location)
        }
    }
}
