/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import at.bitfire.ical4android.Task
import org.dmfs.tasks.contract.TaskContract.Property.Comment

class CommentsHandler : DmfsTaskFieldHandler {
    override fun process(from: ContentValues, to: Task) {
        from.getAsString(Comment.COMMENT)?.let { to.comment = it }
    }
}
