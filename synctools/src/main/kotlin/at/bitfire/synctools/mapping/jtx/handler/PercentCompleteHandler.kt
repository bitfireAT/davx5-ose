/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.PercentComplete

class PercentCompleteHandler : JtxObjectEntityHandler {
    override fun process(from: Entity, main: Entity, to: CalendarComponent) {
        if (to !is VToDo) return

        val percentage = from.entityValues.getAsInteger(JtxContract.JtxICalObject.PERCENT) ?: return
        if (percentage !in 0..100) return

        to += PercentComplete(percentage)
    }
}
