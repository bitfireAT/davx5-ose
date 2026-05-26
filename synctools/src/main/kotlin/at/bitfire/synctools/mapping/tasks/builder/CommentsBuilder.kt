/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Task
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import org.dmfs.tasks.contract.TaskContract.Property.Comment

class CommentsBuilder(
    private val taskList: DmfsTaskList
) : DmfsTaskFieldBuilder {

    override fun build(from: Task, to: Entity) {
        val comment = from.comment ?: return
        to.addSubValue(
            taskList.tasksPropertiesUri(),
            contentValuesOf(
                Comment.MIMETYPE to Comment.CONTENT_ITEM_TYPE,
                Comment.COMMENT to comment
            )
        )
    }

}
