/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import net.fortuna.ical4j.model.component.VToDo
import org.dmfs.tasks.contract.TaskContract.Tasks

class ListIdBuilder(
    private val listId: Long
) : DmfsTaskFieldBuilderVToDo {
    override fun build(from: VToDo, to: Entity) {
        to.entityValues.put(Tasks.LIST_ID, listId)
    }

}
