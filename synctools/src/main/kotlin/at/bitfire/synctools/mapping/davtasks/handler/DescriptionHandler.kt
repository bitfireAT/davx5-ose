/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Description

class DescriptionHandler : DavTaskEntityHandler {
    override fun process(from: Entity, main: Entity, to: VToDo) {
        from.entityValues.getAsString(Tasks.DESCRIPTION)?.let { to += Description(it) }
    }
}
