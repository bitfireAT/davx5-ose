/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Due
import kotlin.jvm.optionals.getOrNull

class DueBuilder(
    private val allDayBuilder: AllDayBuilder
) : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        val due = from.getProperty<Due<*>>(Due.DUE).getOrNull()
        to.entityValues.put(Tasks.DUE, due?.normalizedDate()?.toTimestamp())
        to.entityValues.put(Tasks.DUE_TZ, allDayBuilder.tzId(due))
    }
}
