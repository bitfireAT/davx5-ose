/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.property.Status
import org.dmfs.tasks.contract.TaskContract.Tasks

class StatusHandler : DmfsTaskFieldHandler {

    override fun process(from: ContentValues, to: Task) {
        to.status = when (from.getAsInteger(Tasks.STATUS)) {
            Tasks.STATUS_IN_PROCESS -> Status(Status.VALUE_IN_PROCESS)
            Tasks.STATUS_COMPLETED ->  Status(Status.VALUE_COMPLETED)
            Tasks.STATUS_CANCELLED ->  Status(Status.VALUE_CANCELLED)
            else ->                    Status(Status.VALUE_NEEDS_ACTION)
        }
    }

}
