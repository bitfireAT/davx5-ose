/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.mapping.davtasks.mimeType
import at.bitfire.tasks.contract.TaskProperties
import net.fortuna.ical4j.model.TextList
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Categories

class CategoriesHandler : DavTaskEntityHandler {
    override fun process(from: Entity, main: Entity, to: VToDo) {
        for (row in from.subValues.filter { it.mimeType == TaskProperties.MIMETYPE_CATEGORY }) {
            row.values.getAsString(TaskProperties.DATA1)?.let { to += Categories(TextList(it)) }
        }
    }
}
