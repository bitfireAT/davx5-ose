/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Transp
import net.fortuna.ical4j.model.property.immutable.ImmutableTransp

class AvailabilityHandler: AndroidEventFieldHandler {

    override fun process(from: Entity, main: Entity, to: VEvent) {
        val transp: Transp = when (from.entityValues.getAsInteger(Events.AVAILABILITY)) {
            Events.AVAILABILITY_FREE ->
                ImmutableTransp.TRANSPARENT

            /* Events.AVAILABILITY_BUSY, Events.AVAILABILITY_TENTATIVE */
            else ->
                ImmutableTransp.OPAQUE
        }
        if (transp != ImmutableTransp.OPAQUE)    // iCalendar default value is OPAQUE
            to += transp
    }

}