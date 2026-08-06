/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Status
import net.fortuna.ical4j.model.property.immutable.ImmutableStatus
import kotlin.jvm.optionals.getOrNull

class StatusBuilder : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        val status = from.getProperty<Status>(Status.STATUS).getOrNull()
        to.entityValues.put(Tasks.STATUS, when (status?.value) {
            ImmutableStatus.VALUE_IN_PROCESS -> Tasks.Status.IN_PROCESS
            ImmutableStatus.VALUE_COMPLETED  -> Tasks.Status.COMPLETED
            ImmutableStatus.VALUE_CANCELLED  -> Tasks.Status.CANCELLED
            else                             -> Tasks.Status.NEEDS_ACTION
        })
    }
}
