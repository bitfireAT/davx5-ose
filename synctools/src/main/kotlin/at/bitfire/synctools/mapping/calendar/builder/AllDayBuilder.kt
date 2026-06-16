/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.dtStart
import at.bitfire.synctools.icalendar.isAllDay
import net.fortuna.ical4j.model.component.VEvent
import java.time.temporal.Temporal

class AllDayBuilder : AndroidEventEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val allDay = from.dtStart<Temporal>().isAllDay()
        to.entityValues.put(Events.ALL_DAY, if (allDay) 1 else 0)
    }

}