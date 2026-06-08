/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Due
import org.dmfs.tasks.contract.TaskContract.Tasks
import kotlin.jvm.optionals.getOrNull

class DueBuilder : DmfsTaskFieldBuilder, DmfsTaskFieldBuilderVToDo {

    override fun build(from: Task, to: Entity) {
        to.entityValues.put(Tasks.DUE, from.due?.normalizedDate()?.toTimestamp())
    }

    override fun build(from: VToDo, to: Entity) {
        val due = from.getProperty<Due<*>>(Due.DUE).getOrNull()
        to.entityValues.put(Tasks.DUE, due?.normalizedDate()?.toTimestamp())
    }

}
