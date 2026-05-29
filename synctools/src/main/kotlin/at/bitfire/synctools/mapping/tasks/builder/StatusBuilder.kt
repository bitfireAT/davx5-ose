/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.property.immutable.ImmutableStatus
import org.dmfs.tasks.contract.TaskContract.Tasks

class StatusBuilder : DmfsTaskFieldBuilder {

    override fun build(from: Task, to: Entity) {
        to.entityValues.put(Tasks.STATUS, when (from.status?.value) {
            ImmutableStatus.VALUE_IN_PROCESS -> Tasks.STATUS_IN_PROCESS
            ImmutableStatus.VALUE_COMPLETED  -> Tasks.STATUS_COMPLETED
            ImmutableStatus.VALUE_CANCELLED  -> Tasks.STATUS_CANCELLED
            else                             -> Tasks.STATUS_DEFAULT    // == Tasks.STATUS_NEEDS_ACTION
        })
    }

}
