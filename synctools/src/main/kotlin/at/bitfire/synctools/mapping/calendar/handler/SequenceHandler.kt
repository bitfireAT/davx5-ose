/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.calendar.handler

import android.content.Entity
import android.provider.CalendarContract
import at.bitfire.synctools.icalendar.plusAssign
import at.bitfire.synctools.storage.calendar.EventsContract
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Sequence

class SequenceHandler : AndroidEventEntityHandler {

    override fun process(from: Entity, main: Entity, to: VEvent) {
        val groupScheduled = main.subValues.any { it.uri == CalendarContract.Attendees.CONTENT_URI }
        if (!groupScheduled) {
            /* Don't emit SEQUENCE for non-group-scheduled events. SEQUENCE is only useful/required
            for coordinating group-scheduled events; see RFC 5545 3.8.7.4 Sequence Number,
            RFC 5546 5.3 Sequence Number and RFC 6338 3.2.5 DTSTAMP and SEQUENCE Properties. */
            return
        }

        val seqNo = from.entityValues.getAsInteger(EventsContract.COLUMN_SEQUENCE)
        if (seqNo != null && seqNo > 0)
            to += Sequence(seqNo)
    }

}