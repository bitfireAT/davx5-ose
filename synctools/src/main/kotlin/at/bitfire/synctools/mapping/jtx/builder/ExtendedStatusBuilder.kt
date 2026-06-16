/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import at.bitfire.ical4android.JtxICalObject
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.XProperty
import kotlin.jvm.optionals.getOrNull

class ExtendedStatusBuilder : JtxObjectEntityBuilder {
    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        val extendedStatus = from.getProperty<XProperty>(JtxICalObject.X_PROP_XSTATUS).getOrNull()?.value
        to.entityValues.put(JtxContract.JtxICalObject.EXTENDED_STATUS, extendedStatus)
    }
}
