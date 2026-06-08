/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.synctools.util.trimToNull
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Location
import org.dmfs.tasks.contract.TaskContract.Tasks
import kotlin.jvm.optionals.getOrNull

class LocationBuilder : DmfsTaskFieldBuilder, DmfsTaskFieldBuilderVToDo {

    override fun build(from: Task, to: Entity) {
        to.entityValues.put(Tasks.LOCATION, from.location.trimToNull())
    }

    override fun build(from: VToDo, to: Entity) {
        val location = from.getProperty<Location>(Location.LOCATION).getOrNull()
        to.entityValues.put(Tasks.LOCATION, location?.value.trimToNull())
    }

}
