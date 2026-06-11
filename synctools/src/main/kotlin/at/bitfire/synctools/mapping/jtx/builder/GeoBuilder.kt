/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.builder

import android.content.Entity
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import net.fortuna.ical4j.model.property.Geo
import kotlin.jvm.optionals.getOrNull

class GeoBuilder : JtxObjectEntityBuilder {
    override fun build(from: CalendarComponent, main: CalendarComponent, to: Entity) {
        val geo = from.getProperty<Geo>(Geo.GEO).getOrNull()
        to.entityValues.put(JtxContract.JtxICalObject.GEO_LAT, geo?.latitude?.toDouble())
        to.entityValues.put(JtxContract.JtxICalObject.GEO_LONG, geo?.longitude?.toDouble())
    }
}
