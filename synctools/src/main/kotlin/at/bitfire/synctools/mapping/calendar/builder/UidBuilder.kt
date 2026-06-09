/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import net.fortuna.ical4j.model.component.VEvent
import kotlin.jvm.optionals.getOrNull

class UidBuilder : AndroidEventEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        // Always take UID from main event because exceptions must have the same UID anyway.
        // Note: RFC 5545 requires UID for VEVENTs, however the obsoleted RFC 2445 does not.
        to.entityValues.put(Events.UID_2445, main.uid?.getOrNull()?.value)
    }

}