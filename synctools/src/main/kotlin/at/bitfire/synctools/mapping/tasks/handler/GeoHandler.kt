/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.property.Geo
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.util.logging.Level
import java.util.logging.Logger

class GeoHandler : DmfsTaskFieldHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: ContentValues, to: Task) {
        val geo = from.getAsString(Tasks.GEO) ?: return
        val commaIdx = geo.indexOf(',')
        if (commaIdx < 0) return
        val lng = geo.substring(0, commaIdx)
        val lat = geo.substring(commaIdx + 1)
        try {
            to.geoPosition = Geo(lat.toBigDecimal(), lng.toBigDecimal())
        } catch (e: NumberFormatException) {
            logger.log(Level.WARNING, "Invalid GEO value: $geo", e)
        }
    }

}
