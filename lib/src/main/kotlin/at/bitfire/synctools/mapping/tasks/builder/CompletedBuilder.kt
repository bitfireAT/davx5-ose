/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.ical4android.Task
import org.dmfs.tasks.contract.TaskContract.Tasks

class CompletedBuilder : DmfsTaskFieldBuilder {

    override fun build(from: Task, to: Entity) {
        // COMPLETED must always be a DATE-TIME
        to.entityValues.put(Tasks.COMPLETED, from.completedAt?.date?.toEpochMilli())
        to.entityValues.put(Tasks.COMPLETED_IS_ALLDAY, 0)
    }

}
