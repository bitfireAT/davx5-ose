/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.LastModified
import org.dmfs.tasks.contract.TaskContract.Tasks
import kotlin.jvm.optionals.getOrNull

class LastModifiedBuilder : DmfsTaskFieldBuilder, DmfsTaskFieldBuilderVToDo {

    override fun build(from: Task, to: Entity) {
        to.entityValues.put(Tasks.LAST_MODIFIED, from.lastModified)
    }

    override fun build(from: VToDo, to: Entity) {
        val lastModified = from.getProperty<LastModified>(LastModified.LAST_MODIFIED).getOrNull()
        to.entityValues.put(Tasks.LAST_MODIFIED, lastModified?.normalizedDate()?.toTimestamp())
    }

}
