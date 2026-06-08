/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.synctools.util.trimToNull
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Description
import org.dmfs.tasks.contract.TaskContract.Tasks
import kotlin.jvm.optionals.getOrNull

class DescriptionBuilder : DmfsTaskFieldBuilder, DmfsTaskFieldBuilderVToDo {

    override fun build(from: Task, to: Entity) {
        to.entityValues.put(Tasks.DESCRIPTION, from.description.trimToNull())
    }

    override fun build(from: VToDo, to: Entity) {
        val description = from.getProperty<Description>(Description.DESCRIPTION).getOrNull()
        to.entityValues.put(Tasks.DESCRIPTION, description?.value.trimToNull())
    }

}
