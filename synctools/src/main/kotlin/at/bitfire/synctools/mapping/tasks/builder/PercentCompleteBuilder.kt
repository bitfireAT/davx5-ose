/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.PercentComplete
import org.dmfs.tasks.contract.TaskContract.Tasks
import kotlin.jvm.optionals.getOrNull

class PercentCompleteBuilder : DmfsTaskFieldBuilder, DmfsTaskFieldBuilderVToDo {

    override fun build(from: Task, to: Entity) {
        to.entityValues.put(Tasks.PERCENT_COMPLETE, from.percentComplete)
    }

    override fun build(from: VToDo, to: Entity) {
        val percentComplete = from.getProperty<PercentComplete>(PercentComplete.PERCENT_COMPLETE).getOrNull()
        to.entityValues.put(Tasks.PERCENT_COMPLETE, percentComplete?.percentage)
    }

}
