/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.component.VToDo

class GeoBuilder : DmfsTaskFieldBuilderVToDo {

    override fun build(from: Task, to: VToDo) {
        val position = from.geoPosition ?: return
        to.geographicPos.latitude = position.latitude
        to.geographicPos.longitude = position.longitude
    }

}
