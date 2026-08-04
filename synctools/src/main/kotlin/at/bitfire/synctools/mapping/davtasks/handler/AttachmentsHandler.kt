/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.mapping.davtasks.mimeType
import at.bitfire.tasks.contract.TaskProperties
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.FmtType
import net.fortuna.ical4j.model.property.Attach
import java.net.URI
import java.util.logging.Level
import java.util.logging.Logger

class AttachmentsHandler : DavTaskEntityHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: VToDo) {
        for (row in from.subValues.filter { it.mimeType == TaskProperties.MIMETYPE_ATTACHMENT }) {
            val uriString = row.values.getAsString(TaskProperties.DATA1) ?: continue
            val attach = try {
                Attach(URI.create(uriString))
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Ignoring invalid attachment URI: $uriString", e)
                continue
            }
            row.values.getAsString(TaskProperties.DATA2)?.let { attach += FmtType(it) }
            to += attach
        }
    }
}
