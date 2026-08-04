/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Completed
import kotlin.jvm.optionals.getOrNull

class CompletedBuilder : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        val completed = from.getProperty<Completed>(Completed.COMPLETED).getOrNull()
        // COMPLETED (RFC 5545 §3.8.2.1) must always be a DATE-TIME
        to.entityValues.put(Tasks.COMPLETED, completed?.normalizedDate()?.toTimestamp())
        to.entityValues.put(Tasks.COMPLETED_ALLDAY, false)
    }
}
