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
import net.fortuna.ical4j.model.property.immutable.ImmutableStatus

class StatusHandler: AndroidEventFieldHandler {

    override fun process(from: Entity, main: Entity, to: VEvent) {
        val status = when (from.entityValues.getAsInteger(Events.STATUS)) {
            Events.STATUS_CONFIRMED ->
                ImmutableStatus.VEVENT_CONFIRMED

            Events.STATUS_TENTATIVE ->
                ImmutableStatus.VEVENT_TENTATIVE

            Events.STATUS_CANCELED ->
                ImmutableStatus.VEVENT_CANCELLED

            else ->
                null
        }
        if (status != null)
            to += status
    }

}