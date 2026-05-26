/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Status

class StatusBuilder: AndroidEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        to.entityValues.put(Events.STATUS, when (from.status?.value?.uppercase()) {
            Status.VALUE_CONFIRMED -> Events.STATUS_CONFIRMED
            Status.VALUE_CANCELLED -> Events.STATUS_CANCELED
            null -> null
            else -> Events.STATUS_TENTATIVE
        })
    }

}