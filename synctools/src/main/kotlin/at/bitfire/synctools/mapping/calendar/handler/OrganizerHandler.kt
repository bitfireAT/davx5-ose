/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.Entity
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Organizer
import java.net.URI
import java.net.URISyntaxException
import java.util.logging.Level
import java.util.logging.Logger

class OrganizerHandler: AndroidEventFieldHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: VEvent) {
        // In case of an exception, we're taking ORGANIZER information from the main event and not the exception.
        // See also RFC 6638 3.1 and 3.2.4.2.
        val mainValues = main.entityValues

        // ORGANIZER must only be set for group-scheduled events (= events with attendees)
        val hasAttendees = hasAttendees(from) || hasAttendees(main)
        if (hasAttendees && mainValues.containsKey(Events.ORGANIZER))
            try {
                to += Organizer(URI("mailto", mainValues.getAsString(Events.ORGANIZER), null))
            } catch (e: URISyntaxException) {
                logger.log(Level.WARNING, "Error when creating ORGANIZER mailto URI, ignoring", e)
            }
    }

    private fun hasAttendees(event: Entity) = event.subValues.any { it.uri == CalendarContract.Attendees.CONTENT_URI }

}