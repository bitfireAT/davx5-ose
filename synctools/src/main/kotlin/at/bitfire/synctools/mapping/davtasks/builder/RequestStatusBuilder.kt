/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import androidx.core.content.contentValuesOf
import at.bitfire.synctools.storage.davtasks.DavTaskList
import at.bitfire.tasks.contract.TaskProperties
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.RequestStatus

/** RFC 5545 §3.8.8.3 REQUEST-STATUS (statcode;statdesc;extdata). */
class RequestStatusBuilder(
    private val taskList: DavTaskList
) : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        for (requestStatus in from.getProperties<RequestStatus>(Property.REQUEST_STATUS)) {
            to.addSubValue(
                taskList.tasksPropertiesUri(asSyncAdapter = false),
                contentValuesOf(
                    TaskProperties.MIMETYPE to TaskProperties.MIMETYPE_REQUEST_STATUS,
                    TaskProperties.DATA1 to requestStatus.statusCode,
                    TaskProperties.DATA2 to requestStatus.description,
                    TaskProperties.DATA3 to requestStatus.exData
                )
            )
        }
    }
}
