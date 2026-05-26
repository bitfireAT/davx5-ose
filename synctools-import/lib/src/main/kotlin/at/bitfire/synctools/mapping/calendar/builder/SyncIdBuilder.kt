/*
 * This file is part of bitfireAT/synctools which is released under GPLv3.
 * Copyright © All Contributors. See the LICENSE and AUTHOR files in the root directory for details.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package at.bitfire.synctools.mapping.calendar.builder

import android.content.Entity
import android.provider.CalendarContract.Events
import net.fortuna.ical4j.model.component.VEvent

class SyncIdBuilder(
    private val syncId: String?
): AndroidEntityBuilder {

    override fun build(from: VEvent, main: VEvent, to: Entity) {
        if (from === main) {
            // main event: only set _SYNC_ID
            to.entityValues.put(Events._SYNC_ID, syncId)
            to.entityValues.putNull(Events.ORIGINAL_SYNC_ID)
        } else {

            // exception: set ORIGINAL_SYNC_ID
            to.entityValues.putNull(Events._SYNC_ID)
            to.entityValues.put(Events.ORIGINAL_SYNC_ID, syncId)
        }
    }

}