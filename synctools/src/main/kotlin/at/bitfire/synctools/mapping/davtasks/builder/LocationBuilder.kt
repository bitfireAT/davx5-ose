/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import at.bitfire.synctools.util.trimToNull
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Location
import kotlin.jvm.optionals.getOrNull

class LocationBuilder : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        val location = from.getProperty<Location>(Location.LOCATION).getOrNull()
        to.entityValues.put(Tasks.LOCATION, location?.value.trimToNull())
    }
}
