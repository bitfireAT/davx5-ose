/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import net.fortuna.ical4j.model.component.VEvent

class DirtyAndDeletedBuilder : AndroidEventEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        // DIRTY and DELETED is always unset when we create or update an event row
        to.entityValues.put(Events.DIRTY, 0)
        to.entityValues.put(Events.DELETED, 0)
    }

}