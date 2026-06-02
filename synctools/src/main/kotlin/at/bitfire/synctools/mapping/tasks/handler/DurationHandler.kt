/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.ical4android.Task
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.util.AndroidTimeUtils
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Duration
import org.dmfs.tasks.contract.TaskContract.Tasks

class DurationHandler : DmfsTaskFieldHandler, DmfsTaskFieldHandler2 {

    override fun process(from: ContentValues, to: Task) {
        from.getAsString(Tasks.DURATION)?.let { durationStr ->
            to.duration = Duration(AndroidTimeUtils.parseDuration(durationStr))
        }
    }

    override fun process(from: Entity, main: Entity, to: VToDo) {
        val durationString = from.entityValues.getAsString(Tasks.DURATION)
        if (durationString != null) {
            val duration = AndroidTimeUtils.parseDuration(durationString)
            to += Duration(duration)
        }
    }
}
