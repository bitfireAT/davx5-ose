/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Completed
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.time.Instant

class CompletedHandler : DmfsTaskFieldHandler, DmfsTaskFieldHandler2 {

    override fun process(from: ContentValues, to: Task) {
        from.getAsLong(Tasks.COMPLETED)?.let { epochMillis ->
            to.completedAt = Completed(Instant.ofEpochMilli(epochMillis))
        }
    }

    override fun process(from: Entity, main: Entity, to: VToDo) {
        val epochMillis = from.entityValues.getAsLong(Tasks.COMPLETED)

        if (epochMillis != null) {
            to += Completed(Instant.ofEpochMilli(epochMillis))
        }
    }
}
