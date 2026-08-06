/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.mapping.davtasks.mimeType
import at.bitfire.tasks.contract.TaskProperties
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.RequestStatus

class RequestStatusHandler : DavTaskEntityHandler {
    override fun process(from: Entity, main: Entity, to: VToDo) {
        for (row in from.subValues.filter { it.mimeType == TaskProperties.MIMETYPE_REQUEST_STATUS }) {
            val statusCode = row.values.getAsString(TaskProperties.DATA1) ?: continue
            val description = row.values.getAsString(TaskProperties.DATA2) ?: continue
            val extData = row.values.getAsString(TaskProperties.DATA3) ?: ""
            to += RequestStatus(statusCode, description, extData)
        }
    }
}
