/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import net.fortuna.ical4j.model.component.VEvent

class DirtyAndDeletedBuilder: AndroidEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        // DIRTY and DELETED is always unset when we create or update an event row
        to.entityValues.put(Events.DIRTY, 0)
        to.entityValues.put(Events.DELETED, 0)
    }

}