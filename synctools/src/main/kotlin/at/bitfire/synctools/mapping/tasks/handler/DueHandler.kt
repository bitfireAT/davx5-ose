/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.property.Due
import org.dmfs.tasks.contract.TaskContract.Tasks

class DueHandler : DmfsTaskFieldHandler {

    override fun process(from: ContentValues, to: Task) {
        val epochMillis = from.getAsLong(Tasks.DUE) ?: return

        val allDay = (from.getAsInteger(Tasks.IS_ALLDAY) ?: 0) != 0
        val tzId = from.getAsString(Tasks.TZ)

        to.due = Due(TaskTimeField(epochMillis, tzId, allDay).toTemporal())
    }

}
