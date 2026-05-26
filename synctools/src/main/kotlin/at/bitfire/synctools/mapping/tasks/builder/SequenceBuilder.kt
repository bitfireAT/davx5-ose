/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.ical4android.Task
import org.dmfs.tasks.contract.TaskContract.Tasks

class SequenceBuilder : DmfsTaskFieldBuilder {

    override fun build(from: Task, to: Entity) {
        /* When we build the SYNC_VERSION column from a real task, we set the sequence to 0 (not null), so that we
        can distinguish it from tasks which have been created locally and have never been uploaded yet. */
        to.entityValues.put(Tasks.SYNC_VERSION, from.sequence ?: 0)
    }

}
