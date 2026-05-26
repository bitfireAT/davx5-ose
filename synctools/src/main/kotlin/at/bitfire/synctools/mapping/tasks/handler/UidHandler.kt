/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import at.bitfire.ical4android.Task
import org.dmfs.tasks.contract.TaskContract.Tasks

class UidHandler : DmfsTaskFieldHandler {

    override fun process(from: ContentValues, to: Task) {
        to.uid = from.getAsString(Tasks._UID)
    }

}
