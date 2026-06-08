/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.property.Organizer
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.net.URISyntaxException
import java.util.logging.Level
import java.util.logging.Logger

class OrganizerHandler : DmfsTaskFieldHandler, DmfsTaskFieldHandler2 {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: ContentValues, to: Task) {
        val email = from.getAsString(Tasks.ORGANIZER) ?: return
        try {
            to.organizer = Organizer("mailto:$email")
        } catch (e: URISyntaxException) {
            logger.log(Level.WARNING, "Invalid ORGANIZER email", e)
        }
    }

    override fun process(from: Entity, main: Entity, to: VToDo) {
        val email = from.entityValues.getAsString(Tasks.ORGANIZER)
        if (email != null) {
            try {
                to += Organizer("mailto:$email")
            } catch (e: URISyntaxException) {
                logger.log(Level.WARNING, "Invalid ORGANIZER email", e)
            }
        }
    }
}
