/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Geo
import kotlin.jvm.optionals.getOrNull

class GeoBuilder : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        val geo = from.getProperty<Geo>(Geo.GEO).getOrNull()
        to.entityValues.put(Tasks.GEO_LAT, geo?.latitude?.toDouble())
        to.entityValues.put(Tasks.GEO_LON, geo?.longitude?.toDouble())
    }
}
