/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.tasks.handler

import android.content.ContentValues
import at.bitfire.ical4android.Task
import net.fortuna.ical4j.model.property.Organizer
import org.dmfs.tasks.contract.TaskContract.Tasks
import java.net.URISyntaxException
import java.util.logging.Level
import java.util.logging.Logger

class OrganizerHandler : DmfsTaskFieldHandler {

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

}
