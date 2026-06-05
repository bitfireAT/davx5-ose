/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.ical4android.Task
import at.bitfire.ical4android.UnknownProperty
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.storage.tasks.DmfsTask.Companion.UNKNOWN_PROPERTY_DATA
import net.fortuna.ical4j.model.component.VToDo
import org.json.JSONException
import java.util.logging.Logger

class UnknownPropertiesHandler : DmfsTaskFieldHandler, DmfsTaskFieldHandler2 {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: ContentValues, to: Task) {
        from.getAsString(UNKNOWN_PROPERTY_DATA)?.let { properties ->
            try {
                to.unknownProperties += UnknownProperty.fromJsonString(properties)
            } catch (e: JSONException) {
                // Ignore properties with invalid JSON
                logger.warning("Got an unknown property with invalid JSON: $e")
            }
        }
    }

    override fun process(from: Entity, main: Entity, to: VToDo) {
        from.entityValues.getAsString(UNKNOWN_PROPERTY_DATA)?.let { properties ->
            try {
                to += UnknownProperty.fromJsonString(properties)
            } catch (e: JSONException) {
                // Ignore properties with invalid JSON
                logger.warning("Got an unknown property with invalid JSON: $e")
            }
        }
    }
}
