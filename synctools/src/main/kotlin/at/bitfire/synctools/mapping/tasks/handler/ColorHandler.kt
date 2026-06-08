/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.Css3Color
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Color
import org.dmfs.tasks.contract.TaskContract.Tasks

class ColorHandler : DmfsTaskEntityHandler {

    override fun process(from: Entity, main: Entity, to: VToDo) {
        val color = from.entityValues.getAsInteger(Tasks.TASK_COLOR)
        if (color != null) {
            val colorName = Css3Color.nearestMatch(color).name
            to += Color(null, colorName)
        }
    }
}
