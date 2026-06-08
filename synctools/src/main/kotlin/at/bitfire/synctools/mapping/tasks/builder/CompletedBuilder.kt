/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Completed
import org.dmfs.tasks.contract.TaskContract.Tasks
import kotlin.jvm.optionals.getOrNull

class CompletedBuilder : DmfsTaskEntityBuilder {

    override fun build(from: VToDo, to: Entity) {
        val completed = from.getProperty<Completed>(Completed.COMPLETED).getOrNull()
        // COMPLETED must always be a DATE-TIME
        to.entityValues.put(Tasks.COMPLETED, completed?.normalizedDate()?.toTimestamp())
        to.entityValues.put(Tasks.COMPLETED_IS_ALLDAY, 0)
    }

}
