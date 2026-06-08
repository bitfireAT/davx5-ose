/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Url
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.net.URI
import java.net.URISyntaxException
import java.util.logging.Level
import java.util.logging.Logger

class UrlHandler : DmfsTaskFieldHandler, DmfsTaskFieldHandler2 {

    private val logger
        get() = Logger.getLogger(UrlHandler::class.java.name)

    override fun process(from: ContentValues, to: Task) {
        to.url = from.getAsString(Tasks.URL)
    }

    override fun process(from: Entity, main: Entity, to: VToDo) {
        val url = from.entityValues.getAsString(Tasks.URL)
        if (url != null) {
            try {
                to += Url(URI(url))
            } catch (e: URISyntaxException) {
                // Ignore invalid URLs
                logger.log(Level.WARNING, "Ignoring invalid task URL: $url", e)
            }
        }
    }

}
