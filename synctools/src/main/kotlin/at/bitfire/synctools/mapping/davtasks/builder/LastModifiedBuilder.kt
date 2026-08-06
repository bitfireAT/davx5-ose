/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.LastModified
import kotlin.jvm.optionals.getOrNull

class LastModifiedBuilder : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        val lastModified = from.getProperty<LastModified>(LastModified.LAST_MODIFIED).getOrNull()
        to.entityValues.put(Tasks.LAST_MODIFIED, lastModified?.normalizedDate()?.toTimestamp())
    }
}
