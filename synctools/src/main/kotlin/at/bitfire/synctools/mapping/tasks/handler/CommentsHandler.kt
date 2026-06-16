/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.mapping.tasks.mimeType
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Comment
import org.dmfs.tasks.contract.TaskContract.Property.Comment as DmfsComment

class CommentsHandler : DmfsTaskEntityHandler {

    override fun process(from: Entity, main: Entity, to: VToDo) {
        for (row in from.subValues.filter { it.mimeType == DmfsComment.CONTENT_ITEM_TYPE }) {
            processComment(row.values, to)
        }
    }

    private fun processComment(values: ContentValues, to: VToDo) {
        values.getAsString(DmfsComment.COMMENT)?.let { commentText ->
            to += Comment(commentText)
        }
    }
}
