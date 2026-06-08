/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Uid
import org.dmfs.tasks.contract.TaskContract.Tasks
import kotlin.jvm.optionals.getOrNull

class UidBuilder : DmfsTaskEntityBuilder {

    override fun build(from: VToDo, to: Entity) {
        val uid = from.getProperty<Uid>(Uid.UID).getOrNull()
        to.entityValues.put(Tasks._UID, uid?.value)
    }

}
