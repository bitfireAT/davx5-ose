/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import at.bitfire.ical4android.Task
import at.bitfire.synctools.util.trimToNull
import net.fortuna.ical4j.model.component.VToDo

class LocationBuilder : DmfsTaskFieldBuilderVToDo {

    override fun build(from: Task, to: VToDo) {
        to.location.value = from.location.trimToNull()
    }

}
