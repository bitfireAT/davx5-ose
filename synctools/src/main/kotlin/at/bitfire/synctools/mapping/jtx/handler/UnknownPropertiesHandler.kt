/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.jtx.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.mapping.UnknownProperty
import at.techbee.jtx.JtxContract
import net.fortuna.ical4j.model.component.CalendarComponent
import org.json.JSONException
import java.util.logging.Logger

class UnknownPropertiesHandler : JtxObjectEntityHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: CalendarComponent) {
        for (row in from.subValues.filter { it.uri == JtxContract.JtxUnknown.CONTENT_URI }) {
            val json = row.values.getAsString(JtxContract.JtxUnknown.UNKNOWN_VALUE) ?: continue

            try {
                to += UnknownProperty.fromJsonString(json)
            } catch (e: JSONException) {
                logger.warning("Got an unknown property with invalid JSON: $e")
            }
        }
    }
}
