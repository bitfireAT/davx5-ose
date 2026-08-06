/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import at.bitfire.synctools.util.trimToNull
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Summary
import kotlin.jvm.optionals.getOrNull

class SummaryBuilder : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        val summary = from.getProperty<Summary>(Summary.SUMMARY).getOrNull()
        to.entityValues.put(Tasks.SUMMARY, summary?.value.trimToNull())
    }
}
