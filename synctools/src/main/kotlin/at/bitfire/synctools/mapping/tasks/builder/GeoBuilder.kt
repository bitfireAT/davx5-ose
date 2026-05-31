/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.ical4android.Task
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Geo
import org.dmfs.tasks.contract.TaskContract.Tasks

class GeoBuilder : DmfsTaskFieldBuilder, DmfsTaskFieldBuilderVToDo {

    override fun build(from: Task, to: Entity) {
        to.entityValues.put(Tasks.GEO, from.geoPosition?.let { "${it.longitude},${it.latitude}" })
    }

    override fun build(from: Task, to: VToDo) {
        to += Geo(null, from.geoPosition?.latitude, from.geoPosition?.longitude)
    }

}
