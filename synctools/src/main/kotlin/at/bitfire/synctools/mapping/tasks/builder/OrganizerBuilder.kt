/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.property.Organizer
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.jvm.optionals.getOrNull

class OrganizerBuilder : DmfsTaskFieldBuilder, DmfsTaskFieldBuilderVToDo {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun build(from: Task, to: Entity) {
        val organizer = from.organizer
        if (organizer == null) {
            to.entityValues.putNull(Tasks.ORGANIZER)
            return
        }

        val uri = organizer.calAddress
        val email = if (uri.scheme.equals("mailto", true))
            uri.schemeSpecificPart
        else
            organizer.getParameter<Email>(Parameter.EMAIL).getOrNull()?.value

        if (email != null)
            to.entityValues.put(Tasks.ORGANIZER, email)
        else {
            logger.log(Level.WARNING, "Ignoring ORGANIZER without email address (not supported by Android)", organizer)
            to.entityValues.putNull(Tasks.ORGANIZER)
        }
    }

    override fun build(from: VToDo, to: Entity) {
        val organizer = from.getProperty<Organizer>(Organizer.ORGANIZER).getOrNull()
        if (organizer == null) {
            to.entityValues.putNull(Tasks.ORGANIZER)
            return
        }

        val uri = organizer.calAddress
        val email = if (uri.scheme.equals("mailto", true))
            uri.schemeSpecificPart
        else
            organizer.getParameter<Email>(Parameter.EMAIL).getOrNull()?.value

        if (email != null)
            to.entityValues.put(Tasks.ORGANIZER, email)
        else {
            logger.log(Level.WARNING, "Ignoring ORGANIZER without email address (not supported by Android)", organizer)
            to.entityValues.putNull(Tasks.ORGANIZER)
        }
    }

}
