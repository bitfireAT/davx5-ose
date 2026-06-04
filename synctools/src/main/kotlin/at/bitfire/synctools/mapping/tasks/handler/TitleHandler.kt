/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.ical4android.Task
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Summary
import org.dmfs.tasks.contract.TaskContract.Tasks

class TitleHandler : DmfsTaskFieldHandler, DmfsTaskFieldHandler2 {

    override fun process(from: ContentValues, to: Task) {
        to.summary = from.getAsString(Tasks.TITLE)
    }

    override fun process(from: Entity, main: Entity, to: VToDo) {
        val title = from.entityValues.getAsString(Tasks.TITLE)
        if (title != null) {
            to += Summary(title)
        }
    }

}
