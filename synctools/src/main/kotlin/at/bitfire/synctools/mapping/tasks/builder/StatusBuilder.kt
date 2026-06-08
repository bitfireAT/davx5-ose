/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Status
import net.fortuna.ical4j.model.property.immutable.ImmutableStatus
import org.dmfs.tasks.contract.TaskContract.Tasks
import kotlin.jvm.optionals.getOrNull

class StatusBuilder : DmfsTaskFieldBuilderVToDo {

    override fun build(from: VToDo, to: Entity) {
        val status = from.getProperty<Status>(Status.STATUS).getOrNull()
        to.entityValues.put(Tasks.STATUS, when (status?.value) {
            ImmutableStatus.VALUE_IN_PROCESS -> Tasks.STATUS_IN_PROCESS
            ImmutableStatus.VALUE_COMPLETED  -> Tasks.STATUS_COMPLETED
            ImmutableStatus.VALUE_CANCELLED  -> Tasks.STATUS_CANCELLED
            else                             -> Tasks.STATUS_DEFAULT    // == Tasks.STATUS_NEEDS_ACTION
        })
    }

}
