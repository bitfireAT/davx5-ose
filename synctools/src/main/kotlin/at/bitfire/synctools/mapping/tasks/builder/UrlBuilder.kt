/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.component.VToDo

class UrlBuilder : DmfsTaskFieldBuilderVToDo {

    override fun build(from: Task, to: VToDo) {
        to.url.value = from.url
    }

}
