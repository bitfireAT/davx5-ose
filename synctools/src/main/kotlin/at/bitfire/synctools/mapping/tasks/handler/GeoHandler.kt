/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Geo
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.util.logging.Level
import java.util.logging.Logger

class GeoHandler : DmfsTaskEntityHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)
    override fun process(from: Entity, main: Entity, to: VToDo) {
        val geo = parseGeoString(from.entityValues.getAsString(Tasks.GEO))
        if (geo != null) {
            to += geo
        }
    }

    private fun parseGeoString(geoString: String?): Geo? {
        if (geoString == null) {
            return null
        }

        val commaIdx = geoString.indexOf(',')
        if (commaIdx < 0) {
            return null
        }

        val longitude = geoString.substring(0, commaIdx)
        val latitude = geoString.substring(commaIdx + 1)

        return try {
            Geo(latitude.toBigDecimal(), longitude.toBigDecimal())
        } catch (e: NumberFormatException) {
            logger.log(Level.WARNING, "Invalid GEO value: $geoString", e)
            null
        }
    }
}
