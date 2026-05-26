/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.property.Completed
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.time.Instant

class CompletedHandler : DmfsTaskFieldHandler {

    override fun process(from: ContentValues, to: Task) {
        from.getAsLong(Tasks.COMPLETED)?.let { epochMillis ->
            to.completedAt = Completed(Instant.ofEpochMilli(epochMillis))
        }
    }

}
