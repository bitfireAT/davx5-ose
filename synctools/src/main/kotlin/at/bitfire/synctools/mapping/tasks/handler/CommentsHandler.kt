/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import at.bitfire.ical4android.Task
import org.dmfs.tasks.contract.TaskContract.Property.Comment

class CommentsHandler : DmfsTaskPropertyHandler {
    override fun process(row: ContentValues, to: Task) {
        row.getAsString(Comment.COMMENT)?.let { to.comment = it }
    }
}
