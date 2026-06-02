/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.component.VToDo
import org.dmfs.tasks.contract.TaskContract.Tasks

class DirtyBuilder : DmfsTaskFieldBuilder, DmfsTaskFieldBuilderVToDo {

    override fun build(from: Task, to: Entity) {
        // DIRTY is always unset when we create or update a task row
        to.entityValues.put(Tasks._DIRTY, 0)
    }

    override fun build(from: VToDo, to: Entity) {
        // DIRTY is always unset when we create or update a task row
        to.entityValues.put(Tasks._DIRTY, 0)
    }

}
