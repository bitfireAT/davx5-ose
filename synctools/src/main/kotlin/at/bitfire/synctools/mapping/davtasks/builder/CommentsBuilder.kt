/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.davtasks.DavTaskList
import at.bitfire.tasks.contract.TaskProperties
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.AltRep
import net.fortuna.ical4j.model.parameter.Language
import net.fortuna.ical4j.model.property.Comment
import kotlin.jvm.optionals.getOrNull

/** RFC 5545 §3.8.1.4 COMMENT, with ALTREP (§3.2.1) and LANGUAGE (§3.2.10). */
class CommentsBuilder(
    private val taskList: DavTaskList
) : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        for (comment in from.getProperties<Comment>(Property.COMMENT)) {
            to.addSubValue(
                taskList.tasksPropertiesUri(asSyncAdapter = false),
                contentValuesOf(
                    TaskProperties.MIMETYPE to TaskProperties.MIMETYPE_COMMENT,
                    TaskProperties.DATA1 to comment.value,
                    TaskProperties.DATA2 to comment.getParameter<AltRep>(Parameter.ALTREP).getOrNull()?.value,
                    TaskProperties.DATA3 to comment.getParameter<Language>(Parameter.LANGUAGE).getOrNull()?.value
                )
            )
        }
    }
}
