/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.builder

import android.content.Entity
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.DtStart
import kotlin.jvm.optionals.getOrNull

class StartTimeBuilder(
    private val allDayBuilder: AllDayBuilder
) : DavTaskEntityBuilder {
    override fun build(from: VToDo, to: Entity) {
        val dtStart = from.getProperty<DtStart<*>>(DtStart.DTSTART).getOrNull()
        to.entityValues.put(Tasks.DTSTART, dtStart?.normalizedDate()?.toTimestamp())
        to.entityValues.put(Tasks.DTSTART_TZ, allDayBuilder.tzId(dtStart))
    }
}
