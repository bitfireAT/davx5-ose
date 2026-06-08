/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Geo
import org.dmfs.tasks.contract.TaskContract.Tasks
import kotlin.jvm.optionals.getOrNull

class GeoBuilder : DmfsTaskFieldBuilder, DmfsTaskFieldBuilderVToDo {

    override fun build(from: Task, to: Entity) {
        to.entityValues.put(Tasks.GEO, from.geoPosition?.let { "${it.longitude},${it.latitude}" })
    }

    override fun build(from: VToDo, to: Entity) {
        val geo = from.getProperty<Geo>(Geo.GEO).getOrNull()
        to.entityValues.put(Tasks.GEO, geo?.let { "${it.longitude},${it.latitude}" })
    }

}
