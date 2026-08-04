/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Created
import kotlin.jvm.optionals.getOrNull

class CreatedBuilder : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        val createdAt = from.getProperty<Created>(Created.CREATED).getOrNull()
        to.entityValues.put(Tasks.CREATED, createdAt?.normalizedDate()?.toTimestamp())
    }
}
