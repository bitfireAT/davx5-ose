/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks.builder

import android.content.Entity
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.parameter.Email
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.jvm.optionals.getOrNull

class OrganizerBuilder : DmfsTaskFieldBuilder {

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

}
