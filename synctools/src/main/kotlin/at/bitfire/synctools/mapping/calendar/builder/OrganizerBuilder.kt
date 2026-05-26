/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.property.Organizer
import java.net.URI
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.jvm.optionals.getOrNull

class OrganizerBuilder(
    private val ownerAccount: String
): AndroidEntityBuilder {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val values = to.entityValues
        val groupScheduled = from.getProperties<Attendee>(Property.ATTENDEE).isNotEmpty()
        if (groupScheduled) {
            values.put(Events.HAS_ATTENDEE_DATA, 1)

            // We prefer the ORGANIZER from the main event and not the exception (it must be the same).
            // See RFC 6638 3.1 and 3.2.4.2.
            values.put(Events.ORGANIZER, emailFromOrganizer(main.organizer ?: from.organizer) ?: ownerAccount)

        } else { /* !groupScheduled */
            values.put(Events.HAS_ATTENDEE_DATA, 0)
            values.put(Events.ORGANIZER, ownerAccount)
        }
    }

    fun emailFromOrganizer(organizer: Organizer?): String? {
        if (organizer == null)
            return null

        // Take from mailto: value or EMAIL parameter
        val uri: URI? = organizer.calAddress
        val email = if (uri?.scheme.equals("mailto", true))
            uri?.schemeSpecificPart
        else
            organizer.getParameter<Email>(Parameter.EMAIL).getOrNull()?.value

        if (email != null)
            return email

        logger.log(Level.WARNING, "Ignoring ORGANIZER without email address (not supported by Android)", organizer)
        return null
    }

}