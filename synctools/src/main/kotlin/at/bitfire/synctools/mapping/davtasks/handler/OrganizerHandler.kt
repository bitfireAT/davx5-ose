/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.davtasks.handler

import android.content.Entity
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.tasks.contract.Tasks
import net.fortuna.ical4j.model.component.VToDo
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.SentBy
import net.fortuna.ical4j.model.property.Organizer
import java.net.URI
import java.util.logging.Level
import java.util.logging.Logger

class OrganizerHandler : DavTaskEntityHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: VToDo) {
        val calAddress = from.entityValues.getAsString(Tasks.ORGANIZER) ?: return

        val organizer = Organizer()
        try {
            organizer.calAddress = URI(calAddress)
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Ignoring invalid organizer URI: $calAddress", e)
            return
        }

        from.entityValues.getAsString(Tasks.ORGANIZER_CN)?.let { organizer += Cn(it) }
        from.entityValues.getAsString(Tasks.ORGANIZER_SENT_BY)?.let { organizer += SentBy(it) }

        to += organizer
    }
}
