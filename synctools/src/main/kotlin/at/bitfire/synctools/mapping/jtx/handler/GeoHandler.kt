/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.mapping.jtx.JtxProperty
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.Geo
import net.fortuna.ical4j.model.property.XProperty

class GeoHandler : JtxObjectEntityHandler {
    override fun process(from: Entity, main: Entity, to: CalendarComponent) {
        val latitude = from.entityValues.getAsDouble(JtxContract.JtxICalObject.GEO_LAT)
        val longitude = from.entityValues.getAsDouble(JtxContract.JtxICalObject.GEO_LONG)

        if (latitude != null && longitude != null)
            to += Geo(latitude.toBigDecimal(), longitude.toBigDecimal())

        from.entityValues.getAsInteger(JtxContract.JtxICalObject.GEOFENCE_RADIUS)?.let { geofenceRadius ->
            to += XProperty(JtxProperty.X_GEOFENCE_RADIUS, geofenceRadius.toString())
        }
    }
}
