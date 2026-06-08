/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.DtStart
import org.dmfs.tasks.contract.TaskContract.Tasks
import kotlin.jvm.optionals.getOrNull

class StartTimeBuilder : DmfsTaskEntityBuilder {

    override fun build(from: VToDo, to: Entity) {
        val dtStart = from.getProperty<DtStart<*>>(DtStart.DTSTART).getOrNull()
        to.entityValues.put(Tasks.DTSTART, dtStart?.normalizedDate()?.toTimestamp())
    }

}
