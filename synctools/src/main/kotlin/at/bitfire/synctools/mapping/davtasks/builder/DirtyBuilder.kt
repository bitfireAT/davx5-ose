/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo

class DirtyBuilder : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        // _DIRTY is always unset when we create or update a task row from a sync
        to.entityValues.put(Tasks._DIRTY, false)
    }
}
