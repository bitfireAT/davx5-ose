/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo

class ListIdBuilder(
    private val listId: Long
) : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        to.entityValues.put(Tasks.LIST_ID, listId)
    }
}
