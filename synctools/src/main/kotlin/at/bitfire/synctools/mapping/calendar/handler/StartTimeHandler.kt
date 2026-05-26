/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.exception.InvalidLocalResourceException
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.DtStart

class StartTimeHandler: AndroidEventFieldHandler {

    override fun process(from: Entity, main: Entity, to: VEvent) {
        val values = from.entityValues
        val allDay = (values.getAsInteger(Events.ALL_DAY) ?: 0) != 0

        // DATE or DATE-TIME according to allDay
        val start = AndroidTimeField(
            timestamp = values.getAsLong(Events.DTSTART) ?: throw InvalidLocalResourceException("Missing DTSTART"),
            timeZone = values.getAsString(Events.EVENT_TIMEZONE),
            allDay = allDay
        ).toTemporal()

        to += DtStart(start)
    }

}