/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.util.Utils.trimToNull
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Location

class LocationHandler: AndroidEventFieldHandler {

    override fun process(from: Entity, main: Entity, to: VEvent) {
        val location = from.entityValues.getAsString(Events.EVENT_LOCATION).trimToNull()
        if (location != null)
            to += Location(location)
    }

}