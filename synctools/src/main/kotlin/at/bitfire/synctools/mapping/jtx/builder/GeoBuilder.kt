/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import at.bitfire.synctools.mapping.jtx.JtxProperty
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.Geo
import net.fortuna.ical4j.model.property.XProperty
import kotlin.jvm.optionals.getOrNull

/** Includes `GEO_LAT`, `GEO_LONG` and `GEOFENCE_RADIUS`. */
class GeoBuilder : JtxObjectEntityBuilder {
    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        val geo = from.getProperty<Geo>(Geo.GEO).getOrNull()
        to.entityValues.put(JtxContract.JtxICalObject.GEO_LAT, geo?.latitude?.toDouble())
        to.entityValues.put(JtxContract.JtxICalObject.GEO_LONG, geo?.longitude?.toDouble())

        val geofenceRadius = from.getProperty<XProperty>(JtxProperty.X_GEOFENCE_RADIUS).getOrNull()
        to.entityValues.put(JtxContract.JtxICalObject.GEOFENCE_RADIUS, geofenceRadius?.value?.toIntOrNull())
    }
}
