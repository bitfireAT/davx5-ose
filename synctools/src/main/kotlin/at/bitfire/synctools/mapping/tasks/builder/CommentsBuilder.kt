/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.tasks.DmfsTaskList
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Comment
import org.dmfs.tasks.contract.TaskContract.Property.Comment as DmfsComment

class CommentsBuilder(
    private val taskList: DmfsTaskList
) : DmfsTaskEntityBuilder {

    override fun build(from: VToDo, to: Entity) {
        for (comment in from.getProperties<Comment>(Property.COMMENT)) {
            to.addSubValue(
                taskList.tasksPropertiesUri(),
                contentValuesOf(
                    DmfsComment.MIMETYPE to DmfsComment.CONTENT_ITEM_TYPE,
                    DmfsComment.COMMENT to comment.value
                )
            )
        }
    }

}
