/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import at.bitfire.ical4android.JtxICalObject
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent

class ExtendedStatusBuilder : JtxObjectEntityBuilder {
    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        val extendedStatus = main.propertyList.all.find { it.name == JtxICalObject.X_PROP_XSTATUS }?.value
        to.entityValues.put(JtxContract.JtxICalObject.EXTENDED_STATUS, extendedStatus)
    }
}
