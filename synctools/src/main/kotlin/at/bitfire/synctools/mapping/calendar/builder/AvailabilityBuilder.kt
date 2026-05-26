/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Transp

class AvailabilityBuilder: AndroidEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val availability = when (from.timeTransparency?.value?.uppercase()) {
            Transp.VALUE_TRANSPARENT ->
                Events.AVAILABILITY_FREE

            // Default value in iCalendar is OPAQUE
            else /* including Transp.OPAQUE */ ->
                Events.AVAILABILITY_BUSY
        }

        to.entityValues.put(
            Events.AVAILABILITY,
            availability
        )
    }

}