/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.immutable.ImmutableStatus

class StatusHandler : AndroidEventEntityHandler {

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