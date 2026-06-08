/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.synctools.util.trimToNull
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Summary
import org.dmfs.tasks.contract.TaskContract.Tasks
import kotlin.jvm.optionals.getOrNull

class TitleBuilder : DmfsTaskFieldBuilderVToDo {

    override fun build(from: VToDo, to: Entity) {
        val summary = from.getProperty<Summary>(Summary.SUMMARY).getOrNull()
        to.entityValues.put(Tasks.TITLE, summary?.value.trimToNull())
    }

}
