/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.ical4android.Task
import at.bitfire.synctools.util.AndroidTimeUtils
import org.dmfs.tasks.contract.TaskContract.Tasks

class RecurrenceFieldsBuilder : DmfsTaskFieldBuilder {

    private val allDayBuilder = AllDayBuilder()

    override fun build(from: Task, to: Entity) {
        val allDay = from.isAllDay()
        val tz = if (allDay) null else allDayBuilder.getTimeZone(from)

        to.entityValues.put(Tasks.RRULE, from.rRule?.value)

        to.entityValues.put(Tasks.RDATE,
            if (from.rDates.isEmpty())
                null
            else
                AndroidTimeUtils.recurrenceSetsToOpenTasksString(from.rDates, tz)
        )

        to.entityValues.put(Tasks.EXDATE,
            if (from.exDates.isEmpty())
                null
            else
                AndroidTimeUtils.recurrenceSetsToOpenTasksString(from.exDates, tz)
        )
    }

}
