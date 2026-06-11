/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.XProperty

class XStatusHandler : JtxObjectEntityHandler {
    override fun process(from: Entity, main: Entity, to: CalendarComponent) {
        from.entityValues.getAsString(JtxContract.JtxICalObject.EXTENDED_STATUS)?.let { xStatus ->
            to += XProperty(JtxContract.JtxICalObject.EXTENDED_STATUS, xStatus)
        }
    }
}
