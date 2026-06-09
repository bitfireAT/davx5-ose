/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.util.trimToNull
import net.fortuna.ical4j.model.component.VEvent

class LocationBuilder : AndroidEventEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        to.entityValues.put(Events.EVENT_LOCATION, from.location?.value.trimToNull())
    }

}