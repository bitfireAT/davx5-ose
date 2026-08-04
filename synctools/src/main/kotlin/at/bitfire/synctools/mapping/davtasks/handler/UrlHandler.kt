/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Url
import java.net.URI
import java.net.URISyntaxException
import java.util.logging.Level
import java.util.logging.Logger

class UrlHandler : DavTaskEntityHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: VToDo) {
        val url = from.entityValues.getAsString(Tasks.URL) ?: return
        try {
            to += Url(URI(url))
        } catch (e: URISyntaxException) {
            logger.log(Level.WARNING, "Ignoring invalid task URL: $url", e)
        }
    }
}
