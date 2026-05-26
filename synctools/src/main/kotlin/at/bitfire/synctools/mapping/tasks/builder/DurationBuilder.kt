/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.ical4android.Task
import org.dmfs.tasks.contract.TaskContract.Tasks

class DurationBuilder : DmfsTaskFieldBuilder {

    override fun build(from: Task, to: Entity) {
        to.entityValues.put(Tasks.DURATION, from.duration?.value)
    }

}
