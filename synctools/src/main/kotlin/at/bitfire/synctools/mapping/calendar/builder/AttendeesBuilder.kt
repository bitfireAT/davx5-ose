/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.ContentValues
import android.content.Entity
import android.provider.CalendarContract.Attendees
import androidx.annotation.VisibleForTesting
import at.bitfire.synctools.mapping.calendar.AttendeeMappings
import at.bitfire.synctools.storage.calendar.AndroidCalendar
import net.fortuna.ical4j.model.Parameter
import net.fortuna.ical4j.model.Property
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.Cn
import net.fortuna.ical4j.model.parameter.Email
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.property.Attendee
import kotlin.jvm.optionals.getOrNull

class AttendeesBuilder(
    private val calendar: AndroidCalendar
): AndroidEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        for (attendee in from.getProperties<Attendee>(Property.ATTENDEE))
            to.addSubValue(Attendees.CONTENT_URI, buildAttendee(attendee, from))
    }

    private fun buildAttendee(attendee: Attendee, event: VEvent): ContentValues {
        val values = ContentValues()
        val organizer = organizerEmail(event) ?:
            /* no ORGANIZER, use current account owner as ORGANIZER */
            calendar.ownerAccount ?: calendar.account.name

        val member = attendee.calAddress
        if (member.scheme.equals("mailto", true))   // attendee identified by email
            values.put(Attendees.ATTENDEE_EMAIL, member.schemeSpecificPart)
        else {
            // attendee identified by other URI
            values.put(Attendees.ATTENDEE_ID_NAMESPACE, member.scheme)
            values.put(Attendees.ATTENDEE_IDENTITY, member.schemeSpecificPart)

            attendee.getParameter<Email>(Parameter.EMAIL).ifPresent { email ->
                values.put(Attendees.ATTENDEE_EMAIL, email.value)
            }
        }

        attendee.getParameter<Cn>(Parameter.CN).ifPresent { cn ->
            values.put(Attendees.ATTENDEE_NAME, cn.value)
        }

        // type/relation mapping is complex and thus outsourced to AttendeeMappings
        AttendeeMappings.iCalendarToAndroid(attendee, values, organizer)

        val status = when(attendee.getParameter<PartStat>(Parameter.PARTSTAT).getOrNull()) {
            PartStat.ACCEPTED     -> Attendees.ATTENDEE_STATUS_ACCEPTED
            PartStat.DECLINED     -> Attendees.ATTENDEE_STATUS_DECLINED
            PartStat.TENTATIVE    -> Attendees.ATTENDEE_STATUS_TENTATIVE
            PartStat.DELEGATED    -> Attendees.ATTENDEE_STATUS_NONE
            else /* default: PartStat.NEEDS_ACTION */ -> Attendees.ATTENDEE_STATUS_INVITED
        }
        values.put(Attendees.ATTENDEE_STATUS, status)

        return values
    }

    @VisibleForTesting
    internal fun organizerEmail(event: VEvent): String? {
        event.organizer?.let { organizer ->
            val uri = organizer.calAddress
            return if (uri.scheme.equals("mailto", true))
                uri.schemeSpecificPart
            else
                organizer.getParameter<Email>(Parameter.EMAIL).getOrNull()?.value
        }
        return null
    }

}