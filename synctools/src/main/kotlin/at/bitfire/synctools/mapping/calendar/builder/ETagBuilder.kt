/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import at.bitfire.synctools.storage.calendar.EventsContract
import net.fortuna.ical4j.model.component.VEvent

class ETagBuilder(
    private val eTag: String?,
    private val scheduleTag: String?
) : AndroidEventEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        val values = to.entityValues
        if (from === main) {
            // only set ETag and Schedule-Tag for main event
            values.put(EventsContract.COLUMN_ETAG, eTag)
            values.put(EventsContract.COLUMN_SCHEDULE_TAG, scheduleTag)
        } else {
            values.putNull(EventsContract.COLUMN_ETAG)
            values.putNull(EventsContract.COLUMN_SCHEDULE_TAG)
        }
    }

}