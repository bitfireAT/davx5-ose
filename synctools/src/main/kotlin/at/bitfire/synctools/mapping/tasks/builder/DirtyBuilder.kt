/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.ical4android.Task
import org.dmfs.tasks.contract.TaskContract.Tasks

class DirtyBuilder : DmfsTaskFieldBuilder {

    override fun build(from: Task, to: Entity) {
        // DIRTY is always unset when we create or update a task row
        to.entityValues.put(Tasks._DIRTY, 0)
    }

}
