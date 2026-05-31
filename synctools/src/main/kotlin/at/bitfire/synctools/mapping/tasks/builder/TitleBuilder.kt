/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.ical4android.Task
import at.bitfire.synctools.util.trimToNull
import net.fortuna.ical4j.model.component.VToDo
import org.dmfs.tasks.contract.TaskContract.Tasks

class TitleBuilder : DmfsTaskFieldBuilder, DmfsTaskFieldBuilderVToDo {

    override fun build(from: Task, to: Entity) {
        to.entityValues.put(Tasks.TITLE, from.summary.trimToNull())
    }

    override fun build(from: Task, to: VToDo) {
        to.summary.value = from.summary.trimToNull()
    }

}
