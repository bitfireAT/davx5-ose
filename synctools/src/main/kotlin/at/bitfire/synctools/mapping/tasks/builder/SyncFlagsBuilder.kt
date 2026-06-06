/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.ical4android.Task
import at.bitfire.synctools.storage.tasks.DmfsTasksContract.COLUMN_FLAGS
import net.fortuna.ical4j.model.component.VToDo

class SyncFlagsBuilder(
    private val flags: Int
) : DmfsTaskFieldBuilder, DmfsTaskFieldBuilderVToDo {

    override fun build(from: Task, to: Entity) {
        to.entityValues.put(COLUMN_FLAGS, flags)
    }

    override fun build(from: VToDo, to: Entity) {
        to.entityValues.put(COLUMN_FLAGS, flags)
    }

}
