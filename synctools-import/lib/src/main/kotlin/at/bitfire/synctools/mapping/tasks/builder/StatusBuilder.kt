/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
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
