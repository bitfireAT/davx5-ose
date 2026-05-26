/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import at.bitfire.ical4android.Task
import at.bitfire.synctools.util.AndroidTimeUtils
import net.fortuna.ical4j.model.property.Duration
import org.dmfs.tasks.contract.TaskContract.Tasks

class DurationHandler : DmfsTaskFieldHandler {

    override fun process(from: ContentValues, to: Task) {
        from.getAsString(Tasks.DURATION)?.let { durationStr ->
            to.duration = Duration(AndroidTimeUtils.parseDuration(durationStr))
        }
    }

}
