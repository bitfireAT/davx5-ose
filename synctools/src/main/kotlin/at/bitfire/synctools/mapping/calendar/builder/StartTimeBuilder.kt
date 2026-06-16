/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import at.bitfire.synctools.icalendar.DatePropertyTzMapper.normalizedDate
import at.bitfire.synctools.icalendar.requireDtStart
import at.bitfire.synctools.util.AndroidTimeUtils.androidTimezoneId
import at.bitfire.synctools.util.AndroidTimeUtils.toTimestamp
import net.fortuna.ical4j.model.component.VEvent
import java.time.temporal.Temporal

class StartTimeBuilder : AndroidEventEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val values = to.entityValues

        val dtStart = from.requireDtStart<Temporal>()
        val normalizedDate = dtStart.normalizedDate()

        // start time: UNIX timestamp
        values.put(Events.DTSTART, normalizedDate.toTimestamp())

        // start time: timezone ID
        values.put(Events.EVENT_TIMEZONE, normalizedDate.androidTimezoneId())
    }

}