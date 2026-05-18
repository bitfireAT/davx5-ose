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
import net.fortuna.ical4j.model.property.Description

class DescriptionHandler: AndroidEventFieldHandler {

    override fun process(from: Entity, main: Entity, to: VEvent) {
        val description = from.entityValues.getAsString(Events.DESCRIPTION).trimToNull()
        if (description != null)
            to += Description(description)
    }

}