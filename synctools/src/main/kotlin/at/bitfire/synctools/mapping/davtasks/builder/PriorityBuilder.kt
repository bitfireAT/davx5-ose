/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Priority
import kotlin.jvm.optionals.getOrNull

class PriorityBuilder : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        val priority = from.getProperty<Priority>(Priority.PRIORITY).getOrNull()
        to.entityValues.put(Tasks.PRIORITY, priority?.level ?: Priority.VALUE_UNDEFINED)
    }
}
