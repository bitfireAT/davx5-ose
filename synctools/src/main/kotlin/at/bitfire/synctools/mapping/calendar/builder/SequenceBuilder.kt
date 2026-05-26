/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import at.bitfire.synctools.storage.calendar.EventsContract
import net.fortuna.ical4j.model.component.VEvent

class SequenceBuilder: AndroidEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        /* When we build the SEQUENCE column from a real event, we set the sequence to 0 (not null), so that we
        can distinguish it from events which have been created locally and have never been uploaded yet. */
        to.entityValues.put(EventsContract.COLUMN_SEQUENCE, from.sequence?.sequenceNo ?: 0)
    }

}