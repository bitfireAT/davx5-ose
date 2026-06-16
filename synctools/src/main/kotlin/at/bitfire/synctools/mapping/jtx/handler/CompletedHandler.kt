/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.Entity
import at.bitfire.ical4android.JtxICalObject
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Completed
import net.fortuna.ical4j.model.property.XProperty
import java.time.Instant

class CompletedHandler : JtxObjectEntityHandler {
    override fun process(from: Entity, main: Entity, to: CalendarComponent) {
        if (to !is VToDo) return

        val completed = from.entityValues.getAsLong(JtxContract.JtxICalObject.COMPLETED) ?: return
        to += Completed(Instant.ofEpochMilli(completed))

        from.entityValues.getAsString(JtxContract.JtxICalObject.COMPLETED_TIMEZONE)?.let { tz ->
            to += XProperty(JtxICalObject.X_PROP_COMPLETEDTIMEZONE, tz)
        }
    }
}
