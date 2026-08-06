/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.PercentComplete
import kotlin.jvm.optionals.getOrNull

class PercentCompleteBuilder : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        val percentComplete = from.getProperty<PercentComplete>(PercentComplete.PERCENT_COMPLETE).getOrNull()
        to.entityValues.put(Tasks.PERCENT_COMPLETE, percentComplete?.percentage)
    }
}
