/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.mapping.UnknownProperty
import at.bitfire.synctools.mapping.davtasks.mimeType
import at.bitfire.tasks.contract.TaskProperties
import net.fortuna.ical4j.model.component.VToDo
import org.json.JSONException
import java.util.logging.Logger

class UnknownPropertiesHandler : DavTaskEntityHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: VToDo) {
        for (row in from.subValues.filter { it.mimeType == TaskProperties.MIMETYPE_UNKNOWN_PROPERTY }) {
            row.values.getAsString(TaskProperties.DATA1)?.let { json ->
                try {
                    to += UnknownProperty.fromJsonString(json)
                } catch (e: JSONException) {
                    logger.warning("Got an unknown property with invalid JSON: $e")
                }
            }
        }
    }
}
