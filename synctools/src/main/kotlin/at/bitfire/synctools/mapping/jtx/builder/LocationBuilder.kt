/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.parameter.AltRep
import net.fortuna.ical4j.model.property.Location
import kotlin.jvm.optionals.getOrNull

class LocationBuilder : JtxObjectEntityBuilder {
    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        val location = from.getProperty<Location>(Location.LOCATION).getOrNull()
        val altRep = location?.getParameter<AltRep>(AltRep.ALTREP)?.getOrNull()
        to.entityValues.put(JtxContract.JtxICalObject.LOCATION, location?.value)
        to.entityValues.put(JtxContract.JtxICalObject.LOCATION_ALTREP, altRep?.value)
    }
}
