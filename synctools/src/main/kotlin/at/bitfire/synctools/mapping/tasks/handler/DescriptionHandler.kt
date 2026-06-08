/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Description
import org.dmfs.tasks.contract.TaskContract.Tasks

class DescriptionHandler : DmfsTaskFieldHandler, DmfsTaskFieldHandler2 {

    override fun process(from: ContentValues, to: Task) {
        to.description = from.getAsString(Tasks.DESCRIPTION)
    }

    override fun process(from: Entity, main: Entity, to: VToDo) {
        val description = from.entityValues.getAsString(Tasks.DESCRIPTION)
        if (description != null) {
            to += Description(description)
        }
    }
}
