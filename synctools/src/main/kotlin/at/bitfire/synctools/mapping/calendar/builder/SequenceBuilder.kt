/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import at.bitfire.synctools.storage.calendar.EventsContract
import net.fortuna.ical4j.model.component.VEvent

class SequenceBuilder : AndroidEventEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        /* When we build the SEQUENCE column from a real event, we set the sequence to 0 (not null), so that we
        can distinguish it from events which have been created locally and have never been uploaded yet. */
        to.entityValues.put(EventsContract.COLUMN_SEQUENCE, from.sequence?.sequenceNo ?: 0)
    }

}