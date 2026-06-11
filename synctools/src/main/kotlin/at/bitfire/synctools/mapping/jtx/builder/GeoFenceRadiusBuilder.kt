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

class GeoFenceRadiusBuilder : JtxObjectEntityBuilder {
    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        val geoFenceRadius = from.getProperty<XProperty>(JtxICalObject.X_PROP_GEOFENCE_RADIUS).getOrNull()
        to.entityValues.put(JtxContract.JtxICalObject.GEOFENCE_RADIUS, geoFenceRadius?.value)
    }
}
