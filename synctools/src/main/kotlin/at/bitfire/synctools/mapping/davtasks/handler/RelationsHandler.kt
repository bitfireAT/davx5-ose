/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.mapping.davtasks.mimeType
import at.bitfire.tasks.contract.TaskProperties
import net.fortuna.ical4j.model.ParameterList
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.RelType
import net.fortuna.ical4j.model.property.RelatedTo

class RelationsHandler : DavTaskEntityHandler {
    override fun process(from: Entity, main: Entity, to: VToDo) {
        for (row in from.subValues.filter { it.mimeType == TaskProperties.MIMETYPE_RELATION }) {
            val uid = row.values.getAsString(TaskProperties.DATA1) ?: continue
            val relTypeValue = row.values.getAsString(TaskProperties.DATA2) ?: TaskProperties.RelType.PARENT
            to += RelatedTo(ParameterList(listOf(RelType(relTypeValue))), uid)
        }
    }
}
