/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.parameter.AltRep
import net.fortuna.ical4j.model.property.Location

class LocationHandler : JtxObjectEntityHandler {
    override fun process(from: Entity, main: Entity, to: CalendarComponent) {
        val locationValue = from.entityValues.getAsString(JtxContract.JtxICalObject.LOCATION) ?: return
        val location = Location(locationValue)

        from.entityValues.getAsString(JtxContract.JtxICalObject.LOCATION_ALTREP)?.let { altRep ->
            location += AltRep(altRep)
        }

        to += location
    }
}
