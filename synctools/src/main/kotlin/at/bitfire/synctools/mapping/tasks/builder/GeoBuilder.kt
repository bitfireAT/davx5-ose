/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.component.VToDo
import org.dmfs.tasks.contract.TaskContract.Tasks

class GeoBuilder : DmfsTaskFieldBuilder, DmfsTaskFieldBuilderVToDo {

    override fun build(from: Task, to: Entity) {
        to.entityValues.put(Tasks.GEO, from.geoPosition?.let { "${it.longitude},${it.latitude}" })
    }

    override fun build(from: Task, to: VToDo) {
        val position = from.geoPosition ?: return
        to.geographicPos.latitude = position.latitude
        to.geographicPos.longitude = position.longitude
    }

}
