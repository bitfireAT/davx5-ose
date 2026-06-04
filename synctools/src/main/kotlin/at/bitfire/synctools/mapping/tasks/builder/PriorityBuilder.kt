/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Priority
import org.dmfs.tasks.contract.TaskContract.Tasks
import kotlin.jvm.optionals.getOrNull

class PriorityBuilder : DmfsTaskFieldBuilder, DmfsTaskFieldBuilderVToDo {

    override fun build(from: Task, to: Entity) {
        to.entityValues.put(Tasks.PRIORITY, from.priority)
    }

    override fun build(from: VToDo, to: Entity) {
        val priority = from.getProperty<Priority>(Priority.PRIORITY).getOrNull()
        to.entityValues.put(Tasks.PRIORITY, priority?.level ?: Priority.VALUE_UNDEFINED)
    }

}
