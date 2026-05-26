/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Attendees
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.mapping.calendar.AttendeeMappings
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.parameter.Rsvp
import net.fortuna.ical4j.model.property.Attendee
import java.net.URI
import java.net.URISyntaxException
import java.util.logging.Level
import java.util.logging.Logger

class AttendeesHandler: AndroidEventFieldHandler {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    override fun process(from: Entity, main: Entity, to: VEvent) {
        for (row in from.subValues.filter { it.uri == Attendees.CONTENT_URI })
            populateAttendee(row.values, to)
    }

    private fun populateAttendee(row: ContentValues, to: VEvent) {
        logger.log(Level.FINE, "Read event attendee from calendar provider", row)

        try {
            val attendee: Attendee
            val email = row.getAsString(Attendees.ATTENDEE_EMAIL)
            val idNS = row.getAsString(Attendees.ATTENDEE_ID_NAMESPACE)
            val id = row.getAsString(Attendees.ATTENDEE_IDENTITY)

            if (idNS != null || id != null) {
                // attendee identified by namespace and ID
                attendee = Attendee(URI(idNS, id, null))
                email?.let { attendee += Email(it) }
            } else
                // attendee identified by email address
                attendee = Attendee(URI("mailto", email, null))

            // always add RSVP (offer attendees to accept/decline)
            attendee += Rsvp.TRUE

            row.getAsString(Attendees.ATTENDEE_NAME)?.let { cn -> attendee += Cn(cn) }

            // type/relation mapping is complex and thus outsourced to AttendeeMappings
            AttendeeMappings.androidToICalendar(row, attendee)

            // status
            when (row.getAsInteger(Attendees.ATTENDEE_STATUS)) {
                Attendees.ATTENDEE_STATUS_INVITED -> attendee += PartStat.NEEDS_ACTION
                Attendees.ATTENDEE_STATUS_ACCEPTED -> attendee += PartStat.ACCEPTED
                Attendees.ATTENDEE_STATUS_DECLINED -> attendee += PartStat.DECLINED
                Attendees.ATTENDEE_STATUS_TENTATIVE -> attendee += PartStat.TENTATIVE
                Attendees.ATTENDEE_STATUS_NONE -> { /* no information, don't add PARTSTAT */ }
            }

            to += attendee
        } catch (e: URISyntaxException) {
            logger.log(Level.WARNING, "Couldn't parse attendee information, ignoring", e)
        }
    }

}