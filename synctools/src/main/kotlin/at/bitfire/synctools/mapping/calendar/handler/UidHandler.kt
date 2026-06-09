/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.plusAssign
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Uid

class UidHandler : AndroidEventEntityHandler {

    override fun process(from: Entity, main: Entity, to: VEvent) {
        // Should always be available because AndroidEventHandler ensures there's a UID to be RFC 5545-compliant.
        // However technically it can be null (and no UID is OK according to RFC 2445).
        val uid = main.entityValues.getAsString(Events.UID_2445)
        if (uid != null)
            to += Uid(uid)
    }

}