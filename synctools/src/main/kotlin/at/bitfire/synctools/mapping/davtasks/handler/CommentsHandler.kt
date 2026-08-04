/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.mapping.davtasks.mimeType
import at.bitfire.tasks.contract.TaskProperties
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.AltRep
import net.fortuna.ical4j.model.parameter.Language
import net.fortuna.ical4j.model.property.Comment

class CommentsHandler : DavTaskEntityHandler {
    override fun process(from: Entity, main: Entity, to: VToDo) {
        for (row in from.subValues.filter { it.mimeType == TaskProperties.MIMETYPE_COMMENT }) {
            val text = row.values.getAsString(TaskProperties.DATA1) ?: continue
            val comment = Comment(text)
            row.values.getAsString(TaskProperties.DATA2)?.let { comment += AltRep(it) }
            row.values.getAsString(TaskProperties.DATA3)?.let { comment += Language(it) }
            to += comment
        }
    }
}
