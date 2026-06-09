/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import at.bitfire.synctools.storage.calendar.EventsContract
import net.fortuna.ical4j.model.component.VEvent

class SyncFlagsBuilder(
    private val flags: Int
) : AndroidEventEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        to.entityValues.put(EventsContract.COLUMN_FLAGS, flags)
    }

}