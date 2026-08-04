/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Uid
import kotlin.jvm.optionals.getOrNull

class UidBuilder : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        val uid = from.getProperty<Uid>(Uid.UID).getOrNull()
        to.entityValues.put(Tasks._UID, uid?.value)
    }
}
